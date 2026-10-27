package org.evd.game.DBService.storage.mysql;

import org.evd.game.runtime.util.RuntimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * // 1. 等待前面的数据库任务
 * CompletableFuture<Void> ready = ...;
 *
 * // 2. 当前数据库任务
 * Supplier<Mono<Integer>> operation = () -> queryDatabase();
 *
 * // 3. 当前任务完成的信号
 * CompletableFuture<Void> done = new CompletableFuture<>();
 *
 * // 4. 前面的任务结束后，执行当前任务
 * ready.whenComplete((ignored, error) -> {
 *
 *     // 真正启动 R2DBC 数据库操作
 *     operation.get().subscribe(
 *         result -> System.out.println(result),
 *         failure -> done.complete(null),
 *         () -> done.complete(null)
 *     );
 * });
 *
 * 这里的CompletableFuture 其实主要就是用来做异步完成通知和任务依赖管理的。
 * ready：等待前面的任务完成，相当于一个异步等待信号。
 * done：当前任务执行完毕后，调用 complete(null) 通知后续任务。
 * whenComplete()：注册回调，收到完成通知后执行指定代码。
 * allOf()：把多个 Future 的完成信号组合起来，等它们全部完成。
 */

/**
 * 基于 Tail Future 的异步数据库串行执行器。
 *
 * <p>语义：
 * <ul>
 *   <li>调用 submit/submitMultiKey/submitBatch 时立即预约并启动调度；返回的 Mono 只观察结果。
 *       不订阅仍执行，重复订阅不重复执行，取消订阅不取消已预约的数据库操作。</li>
 *   <li>同一 Lane 的数据库操作按预约顺序执行；一次跨 Lane 操作原子预约全部 Lane，且只执行一次。</li>
 *   <li>operation 返回的 Mono 必须覆盖 SQL 执行及事务提交/回滚的完整生命周期，
 *       不能在数据库仍在执行时就完成。</li>
 *   <li>operation 由执行器独立订阅，不继承调用方订阅返回 Mono 时的 Reactor Context。
 *       R2DBC 事务必须在 operation 内建立，例如：sql().as(transactionalOperator::transactional)。</li>
 *   <li>本类不创建线程；Future 回调和 operation 订阅可能运行在 Netty EventLoop，
 *       不能执行阻塞操作，也不能把后续回调假定为原调用者的业务线程。</li>
 *   <li>禁止在尚未完成的 operation 内等待同一执行器后来预约的任务，
 *       即使后续任务的 key 不同，也可能因跨 Lane 预约而形成等待环。</li>
 *   <li>只保证当前实例内部的顺序；MySQL 真实事务、跨进程并发及数据库结果未知的分类由上层负责。</li>
 * </ul>
 *
 * <p>done 和 result 故意分开：done 只表示数据库操作已结束、Lane 可继续；
 * result 把操作结果提供给用户。后续数据库操作可以先于前一个任务的业务结果回调开始。
 */
public final class DbSerialExecutor {

    private static final Logger log = LoggerFactory.getLogger(DbSerialExecutor.class);

    /**
     * 线程内的蹦床调度（Trampoline），防止 Future 同步回调导致递归过深、栈溢出。
     *
     * CompletableFuture.whenComplete() 可能在当前线程同步执行回调，
     * 而 Mono.empty()、Mono.just() 等操作也可能在 subscribe() 时立即完成。
     *
     * 如果大量任务连续同步完成，可能形成：
     * finish(A1) -> done.complete() -> start(A2) -> finish(A2) -> ...
     * 导致调用栈不断加深，最终 StackOverflowError。
     *
     * 通过 ThreadLocal 维护每个线程独立的任务队列，将递归调用转换为循环执行。
     * 当前线程正在执行队列时，新任务只入队，不会立即嵌套执行。
     *
     * 不创建额外线程，不保证 Lane 的线程归属，仅用于避免同步回调递归。
     */
    // 解决大量已完成 Future 同步触发时的深层递归；不创建新线程。
    private static final ThreadLocal<DispatchState> INLINE_DISPATCH =
            ThreadLocal.withInitial(DispatchState::new);

    private final Object enqueueLock = new Object();
    private final Lane[] lanes;
    private final int maxPendingPerLane;
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

    // closing、Lane.tail/pending 均由 enqueueLock 保护。
    private boolean closing;

    public DbSerialExecutor(int laneCount, int maxPendingPerLane) {
        if (laneCount <= 0) {
            throw new IllegalArgumentException("laneCount must be greater than 0: " + laneCount);
        }
        if (maxPendingPerLane <= 0) {
            throw new IllegalArgumentException(
                    "maxPendingPerLane must be greater than 0: " + maxPendingPerLane);
        }
        this.maxPendingPerLane = maxPendingPerLane;
        this.lanes = new Lane[laneCount];
        for (int i = 0; i < laneCount; i++) {
            lanes[i] = new Lane(i);
        }
    }

    /** 同一 key（或映射到相同 Lane 的不同 key）的数据库操作按预约顺序执行。 */
    public <T> Mono<T> submit(Object key, Supplier<Mono<T>> operation) {
        Objects.requireNonNull(key, "key");
        return enqueue(List.of(laneOf(key)), operation);
    }

    /**
     * 原子预约所有 key 对应的 Lane，operation 仅执行一次。
     * 注意：此方法不自动开启 MySQL 事务；必须在 operation 内创建真实事务。
     */
    public <T> Mono<T> submitTransaction(Collection<?> keys, Supplier<Mono<T>> operation) {
        Objects.requireNonNull(keys, "keys");
        if (keys.isEmpty()) {
            return Mono.error(new IllegalArgumentException("transaction keys 不能为空"));
        }
        return enqueue(lanesFor(keys), operation);
    }

    /** submitTransaction 的等价名称，用于不一定需要 MySQL 事务的跨 key 操作。 */
    public <T> Mono<T> submitMultiKey(Collection<?> keys, Supplier<Mono<T>> operation) {
        return submitTransaction(keys, operation);
    }

    /**
     * 按 Lane 分组的批量提交。每组单独执行，组间可并行，不是跨 Lane 的数据库事务。
     * 全部组在一次加锁中原子预约：要么全部预约成功，要么全部拒绝。
     * 返回值按分组首次出现顺序排列；Mono.empty() 对应的组不产生结果元素。
     * 结果等到全部组完成再返回，多个组出错时保留延迟错误聚合行为。
     */
    public <T, R> Mono<List<R>> submitBatch(List<T> items,
                                            Function<T, Object> keyExtractor,
                                            Function<List<T>, Mono<R>> operation) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        Objects.requireNonNull(operation, "operation");
        if (items.isEmpty()) {
            return Mono.just(List.of());
        }

        // 在加锁预约前完成输入校验、key 计算和分组，避免执行外部代码时持锁。
        Map<Integer, List<T>> grouped = new LinkedHashMap<>();
        for (T item : items) {
            Objects.requireNonNull(item, "batch item");
            Object key = Objects.requireNonNull(keyExtractor.apply(item), "batch item key");
            int index = RuntimeUtils.mod(key, lanes.length);
            grouped.computeIfAbsent(index, ignored -> new ArrayList<>()).add(item);
        }
        List<BatchGroup<T>> groups = new ArrayList<>(grouped.size());
        List<Lane> reservationLanes = new ArrayList<>(grouped.size());
        for (Map.Entry<Integer, List<T>> entry : grouped.entrySet()) {
            Lane lane = lanes[entry.getKey()];
            groups.add(new BatchGroup<>(lane, entry.getValue()));
            reservationLanes.add(lane);
        }

        List<Task<R>> tasks = new ArrayList<>(groups.size());
        boolean reservationAccepted;
        synchronized (enqueueLock) {
            reservationAccepted = validateReservationLocked(reservationLanes);
            if (reservationAccepted) {
                // 先把全部 Task/依赖构建好，再改写任意 tail。
                // 避免构建第 N 个 Task 出错后，前 N-1 个 Lane 已被错误预约。
                for (BatchGroup<T> group : groups) {
                    tasks.add(prepareLocked(List.of(group.lane),
                            () -> operation.apply(group.items)));
                }
                for (Task<R> task : tasks) {
                    commitLocked(task);
                }
            }
        }
        if (!reservationAccepted) {
            return Mono.error(new IllegalStateException(
                    "DbSerialExecutor reservation rejected; see error log"));
        }

        for (Task<R> task : tasks) {
            start(task);
        }
        // Flux.fromIterable(tasks)：把 Task 列表变成一个结果流。
        return Flux.fromIterable(tasks)
                /*
                task -> resultMono(task.result)	将每个任务的结果转换成 Mono，等待它完成
                tasks.size()	最大并发订阅数量，允许同时等待所有任务
                1	每个内部 Publisher 的预取数量，不是并发数
                 */
                .flatMapSequentialDelayError(task -> resultMono(task.result), tasks.size(), 1)
                .collectList();// collectList()：把各组输出收集成列表，得到方法声明的 Mono<List<R>>。
    }

    /** 立即拒绝新预约；已预约的数据库操作执行完后关闭。 */
    public void close() {
        closeAsync(null);
    }

    /** 与原有 API 兼容；只有首次关闭的 closeAction 会执行。 */
    public void close(Runnable closeAction) {
        closeAsync(closeAction);
    }

    /**
     * 返回关闭完成 Future。完成代表全部已预约数据库操作排空、closeAction 已运行，
     * 不保证所有外部结果观察者（Mono 订阅者）的业务回调也已完成。
     */
    public CompletableFuture<Void> closeAsync() {
        return closeAsync(null);
    }

    /**
     * closeAction 在触发最后一个完成信号的线程中执行，必须非阻塞。
     * 重复调用关闭操作返回相同 Future，忽略之后的 closeAction。
     */
    public CompletableFuture<Void> closeAsync(Runnable closeAction) {
        CompletableFuture<?>[] tails;
        synchronized (enqueueLock) {
            if (closing) {
                return closeFuture;
            }
            closing = true;
            tails = new CompletableFuture<?>[lanes.length];
            for (int i = 0; i < lanes.length; i++) {
                tails[i] = lanes[i].tail;
            }
        }
        // 新任务在锁中已经被禁止，快照覆盖所有已预约任务。
        CompletableFuture.allOf(tails).whenComplete((ignored, error) -> dispatch(() -> {
            Throwable failure = error == null ? null : unwrap(error);
            try {
                if (closeAction != null) {
                    closeAction.run();
                }
            } catch (Throwable closeFailure) {
                log.error("DbSerialExecutor close action failed", closeFailure);
                if (failure == null) {
                    failure = closeFailure;
                } else if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure == null) {
                closeFuture.complete(null);
            } else {
                closeFuture.completeExceptionally(failure);
            }
        }));
        return closeFuture;
    }

    private <T> Mono<T> enqueue(List<Lane> reservationLanes, Supplier<Mono<T>> operation) {
        Objects.requireNonNull(operation, "operation");
        Task<T> task;
        boolean reservationAccepted;
        synchronized (enqueueLock) {
            reservationAccepted = validateReservationLocked(reservationLanes);
            if (reservationAccepted) {
                task = prepareLocked(reservationLanes, operation);
                commitLocked(task);
            } else {
                task = null;
            }
        }
        if (!reservationAccepted) {
            return Mono.error(new IllegalStateException(
                    "DbSerialExecutor reservation rejected; see error log"));
        }
        start(task);
        return resultMono(task.result);
    }

    private Lane laneOf(Object key) {
        return lanes[RuntimeUtils.mod(key, lanes.length)];
    }

    private List<Lane> lanesFor(Collection<?> keys) {
        Set<Integer> indexes = new LinkedHashSet<>();
        for (Object key : keys) {
            Objects.requireNonNull(key, "transaction key");
            indexes.add(RuntimeUtils.mod(key, lanes.length));
        }
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        List<Lane> result = new ArrayList<>(indexes.size());
        for (int index : indexes) {
            result.add(lanes[index]);
        }
        return result;
    }

    // 以下方法只允许在 enqueueLock 内调用。
    private boolean validateReservationLocked(List<Lane> reservationLanes) {
        if (closing) {
            log.error("DbSerialExecutor reservation rejected: executor is closed, lanes={}",
                    laneIndexes(reservationLanes));
            return false;
        }
        for (Lane lane : reservationLanes) {
            if (lane.pending >= maxPendingPerLane && lane.pending % 50 == 0) {
                log.error("DbSerialExecutor pending task limit exceeded: lane={}, pending={}, maxPendingPerLane={}",
                        lane.index, lane.pending, maxPendingPerLane);
            }
        }
        return true;
    }

    private <T> Task<T> prepareLocked(List<Lane> reservationLanes,
                                      Supplier<Mono<T>> operation) {
        CompletableFuture<?>[] previous = new CompletableFuture<?>[reservationLanes.size()];
        for (int i = 0; i < reservationLanes.size(); i++) {
            previous[i] = reservationLanes.get(i).tail;
        }
        // 这里是将所有的tail挂在CompletableFuture前面,将ready变为尾巴节点;
        // 固定快照。之后即使某个 Lane 的 tail 被替换，也不会改变本任务等待的目标。
        CompletableFuture<Void> ready = CompletableFuture.allOf(previous);
        return new Task<>(List.copyOf(reservationLanes), ready, operation);
    }

    private void commitLocked(Task<?> task) {
        for (Lane lane : task.lanes) {
            lane.tail = task.done;
            lane.pending++;
        }
    }

    // 不在 enqueueLock 内启动 SQL，防止同步执行的 Mono 在持锁期间回调业务。
    private void start(Task<?> task) {
        try {
            // whenComplete: 直接在当前线程里执行，当 Future 执行完成时，无论成功还是失败，都会执行指定的回调。
            task.ready.whenComplete((ignored, failure) -> dispatch(() -> {
                if (failure != null) {
                    // 理论上不发生：所有内部 done 都是正常完成；防御性地失败关闭该任务。
                    finish(task, null, unwrap(failure));
                    return;
                }
                try {
                    run(task);
                } catch (Throwable unexpected) {
                    // 不能证明 SQL 未发出，因此将结果不确定错误返回给调用方。
                    finish(task, null, new OutcomeUnknownException(
                            "Unexpected task execution failure; database outcome is unknown", unexpected));
                }
            }));
        } catch (Throwable unexpected) {
            finish(task, null, unexpected);
        }
    }

    private <T> void run(Task<T> task) {
        try {
            // defer: 直到所有旧 Tail 完成后才创建、订阅 operation。
            // Mono.defer() —— 延迟执行; 只有运行 mono.subscribe(); 才执行;
            Mono<Void> running = Mono.defer(() -> Objects.requireNonNull(
                            task.operation.get(), "operation returned null"))
                    .doOnNext(value -> task.value = value) // doOnNext() —— 保存结果
                    .then(); // then() —— 丢弃结果，只关注完成状态 假设原本是：Mono<Player>调用 .then() 后变成：Mono<Void>; 它仍然会等待整个数据库操作完成，只是不再向下游传递查询结果。

            // 独立订阅保证提交后立即执行，与调用方是否订阅/取消返回 Mono 无关。
            /*
             第一个参数 收到数据时执行 ignored -> { } 当 Mono 发出数据（onNext 上面的是doOnNext 不一样）时执行。
                但你的 running 是： Mono<Void> running，因为前面调用了 .then()，已经把结果丢弃了，所以这个回调实际上不会被调用。
              第二个参数：执行失败时
              三个参数：执行成功时
             */
            running.subscribe(
                    ignored -> { },
                    error -> finish(task, null, error),
                    () -> finish(task, task.value, null));
        } catch (Throwable unexpected) {
            // 对于 subscribe 非正常抛出，无法证明 SQL 是否已经送达。
            finish(task, null, new OutcomeUnknownException(
                    "Database subscription unexpectedly failed; outcome is unknown", unexpected));
        }
    }

    /**
     * 扣减 pending 后完成内部 done，再通知外部 result。
     * done 触发的后继任务在内部 dispatch 队列中排在 result 回调前，
     * 避免结果观察者同步等待后继任务时把调度卡在当前栈帧里。
     */
    private <T> void finish(Task<T> task, T value, Throwable error) {
        // 每个任务只允许执行一次
        if (!task.finishing.compareAndSet(false, true)) {
            return;
        }
        dispatch(() -> {
            Throwable finalError = error;
            try {
                synchronized (enqueueLock) {
                    // pend 减去
                    for (Lane lane : task.lanes) {
                        lane.pending--;
                    }
                }
            } catch (Throwable bookkeepingFailure) {
                log.error("DbSerialExecutor task bookkeeping failed: lanes={}",
                        laneIndexes(task.lanes), bookkeepingFailure);
                finalError = bookkeepingFailure;
            } finally {
                // 内部占位 Future 只代表「该任务已终止」，不传播数据库错误。
                // 数据库错误通过 result 报告；done 只负责放行后继任务。   进行后续任务;将future结果设置为null;
                task.done.complete(null);
            }

            Throwable reportError = finalError;
            dispatch(() -> {
                if (reportError == null) {
                    task.result.complete(value);
                } else {
                    task.result.completeExceptionally(reportError);
                }
            });
        });
    }

    private static void dispatch(Runnable action) {
        DispatchState state = INLINE_DISPATCH.get();
        state.queue.addLast(action);
        if (state.draining) {
            return;
        }
        state.draining = true;
        try {
            Runnable next;
            while ((next = state.queue.pollFirst()) != null) {
                try {
                    next.run();
                } catch (Throwable failure) {
                    log.error("DbSerialExecutor inline dispatch failed", failure);
                }
            }
        } finally {
            state.draining = false;
        }
    }

    private static List<Integer> laneIndexes(List<Lane> taskLanes) {
        List<Integer> indexes = new ArrayList<>(taskLanes.size());
        for (Lane lane : taskLanes) {
            indexes.add(lane.index);
        }
        return indexes;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> Mono<T> resultMono(CompletableFuture<T> result) {
        // false 会在用户取消订阅时尝试 cancel Future，破坏已预约操作的生命周期。
        return Mono.fromFuture(result, true);
    }

    private static final class DispatchState {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean draining;
    }

    private static final class Lane {
        private final int index;
        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
        private int pending;
        private Lane(int index) {
            this.index = index;
        }
    }

    private static final class BatchGroup<T> {
        private final Lane lane;
        private final List<T> items;

        private BatchGroup(Lane lane, List<T> items) {
            this.lane = lane;
            this.items = items;
        }
    }

    private static final class Task<T> {
        private final List<Lane> lanes;
        private final CompletableFuture<Void> ready;
        private final Supplier<Mono<T>> operation;
        /**
         * ，done 表示“这个任务结束了，可以放行后续任务”；result 表示“这个任务的执行结果是什么”。把它们分开，前一个任务失败时就不会连带让队列里的后续任务也失败。
         */
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final AtomicBoolean finishing = new AtomicBoolean();
        private volatile T value;

        private Task(List<Lane> lanes, CompletableFuture<Void> ready,
                     Supplier<Mono<T>> operation) {
            this.lanes = lanes;
            this.ready = ready;
            this.operation = operation;
        }
    }

    /** 数据库最终执行/提交/回滚结果无法确认时由数据库层显式抛出。 */
    public static final class OutcomeUnknownException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public OutcomeUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
