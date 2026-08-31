package org.evd.game.DBService.storage.mysql;

import org.evd.game.runtime.util.RuntimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/** 按 key 固定分片，并保证每个分片内异步任务严格串行执行。 */
public final class DbSerialExecutor {

    private static final Logger log = LoggerFactory.getLogger(DbSerialExecutor.class);

    private final Lane[] lanes;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicInteger remainingLanes;
    private final AtomicReference<Runnable> closeAction = new AtomicReference<>();

    public DbSerialExecutor(int laneCount, int maxPendingPerLane) {
        if (laneCount <= 0) {
            throw new IllegalArgumentException("laneCount must be greater than 0: " + laneCount);
        }
        if (maxPendingPerLane <= 0) {
            throw new IllegalArgumentException(
                    "maxPendingPerLane must be greater than 0: " + maxPendingPerLane);
        }
        lanes = new Lane[laneCount];
        remainingLanes = new AtomicInteger(laneCount);
        for (int i = 0; i < laneCount; i++) {
            lanes[i] = new Lane(i, maxPendingPerLane);
        }
    }

    /** operation 延迟到任务真正轮到该 Lane 时才调用。 */
    public <T> Mono<T> submit(Object key, Supplier<Mono<T>> operation) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        Lane lane = RuntimeUtils.mod(key, lanes);
        DbTask<T> task = new DbTask<>(operation);
        Sinks.EmitResult emitResult;
        synchronized (lane.emitLock) {
            if (closing.get()) {
                return Mono.error(new IllegalStateException("DbSerialExecutor is closed"));
            }
            // submit 和 close 必须共用这把锁，避免“检查关闭状态”和“向 Sink 发消息”之间出现竞态。
            emitResult = lane.sink.tryEmitNext(task);
        }
        if (emitResult.isFailure()) {
            IllegalStateException failure = new IllegalStateException(
                    "DbSerialExecutor submit failed: key=" + key + ", lane="
                            + lane.index + ", emitResult=" + emitResult);
            // 有界队列满时 tryEmitNext 不会阻塞，而是返回失败；这里把失败转成调用方能收到的 Mono 错误。
            task.result.tryEmitError(failure);
            log.warn("DbSerialExecutor submit failed: key={}, lane={}, emitResult={}",
                    key, lane.index, emitResult);
        }
        return task.result.asMono();
    }

    /** 按每条数据的 key 分 Lane 提交多个子任务，并等待全部子任务结束。 */
    public <T, R> Mono<List<R>> submitBatch(List<T> items,
                                             Function<T, Object> keyExtractor,
                                             Function<List<T>, Mono<R>> operation) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        Objects.requireNonNull(operation, "operation");
        if (items.isEmpty()) {
            return Mono.just(List.of());
        }

        Map<Integer, List<T>> laneItems = new LinkedHashMap<>();
        for (T item : items) {
            Object key = Objects.requireNonNull(keyExtractor.apply(item), "batch item key");
            int laneIndex = RuntimeUtils.mod(key, lanes.length);
            laneItems.computeIfAbsent(laneIndex, ignored -> new ArrayList<>()).add(item);
        }

        List<Mono<R>> results = new ArrayList<>(laneItems.size());
        for (List<T> laneItemList : laneItems.values()) {
            Object key = keyExtractor.apply(laneItemList.getFirst());
            results.add(submit(key, () -> operation.apply(List.copyOf(laneItemList))));
        }
        // 延迟传播错误，确保其他 Lane 已排队的子 batch 也完成后父方法才返回。
        return Flux.fromIterable(results)
                .flatMapDelayError(mono -> mono, results.size(), 1)
                .collectList();
    }

    public void close() {
        close(null);
    }

    /** 拒绝新任务，排空各 Lane 已入队任务后执行 closeAction。 */
    public void close(Runnable closeAction) {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        this.closeAction.set(closeAction);
        for (Lane lane : lanes) {
            synchronized (lane.emitLock) {
                // complete 只表示不再接收新元素，队列中已经存在的任务仍会继续被 concatMap 消费。
                Sinks.EmitResult result = lane.sink.tryEmitComplete();
                if (result.isFailure() && result != Sinks.EmitResult.FAIL_TERMINATED) {
                    log.warn("DbSerialExecutor lane close failed: lane={}, result={}",
                            lane.index, result);
                }
            }
        }
    }

    private void onLaneClosed() {
        if (remainingLanes.decrementAndGet() == 0) {
            Runnable action = closeAction.get();
            if (action != null) {
                action.run();
            }
        }
    }

    private final class Lane {
        private final int index;
        private final Object emitLock = new Object();
        // Many 可以接收多个任务；unicast 限制只有一个下游订阅者，正好对应本 Lane 的唯一消费链。
        private final Sinks.Many<DbTask<?>> sink;
        @SuppressWarnings("unused")
        private final Disposable subscription;

        private Lane(int index, int maxPendingPerLane) {
            this.index = index;
            if (maxPendingPerLane == Integer.MAX_VALUE) {
                // 无界缓冲不会因容量耗尽而拒绝任务，但待处理任务过多会增加内存压力。
                sink = Sinks.many().unicast().onBackpressureBuffer();
            } else {
                Queue<DbTask<?>> queue = Queues.<DbTask<?>>get(maxPendingPerLane).get();
                // 有界缓冲在队列满时让 tryEmitNext 返回失败，而不是阻塞提交线程。
                sink = Sinks.many().unicast().onBackpressureBuffer(queue);
            }
            subscription = sink.asFlux()
                    // concatMap 会等前一个 run() 完成后，才订阅下一个任务。
                    // 所以同一 Lane 内最多同时执行一个 DbTask，不同 Lane 之间仍可并行。
                    .concatMap(DbTask::run)
                    // 无论 complete、error 还是 cancel，都要统计这个 Lane 已经终止。
                    .doFinally(signalType -> onLaneClosed())
                    // DbTask.run 已经把单任务错误转发给调用方并恢复为空；这里只记录逃出任务链的异常。
                    .subscribe(null, error -> log.error(
                            "DbSerialExecutor lane terminated: lane={}", index, error));
        }
    }

    private static final class DbTask<T> {
        private final Supplier<Mono<T>> operation;
        // One 只能发出一个值、空完成或一个错误，正好对应 submit 返回的 Mono<T>。
        private final Sinks.One<T> result = Sinks.one();

        private DbTask(Supplier<Mono<T>> operation) {
            this.operation = operation;
        }

        private Mono<Void> run() {
            return Mono.defer(() -> {
                        // defer 让 Supplier 在 concatMap 订阅本任务时才执行，而不是 submit 时执行。
                        Mono<T> operationMono = operation.get();
                        if (operationMono == null) {
                            return Mono.error(new NullPointerException("operation returned null"));
                        }
                        return operationMono;
                    })
                    // doOnNext 只观察并转发值，不改变上游 Mono 的信号流。 result.tryEmitValue(value),将结果转到Sinks.One里面去
                    .doOnNext(result::tryEmitValue)
                    // Mono empty 不会触发 doOnNext，所以需要显式向结果 Sink 发送空完成。
                    .doOnSuccess(value -> {
                        if (value == null) {
                            result.tryEmitEmpty();
                        }
                    })
                    // 先把错误交给调用方的 result，再在本 Lane 内恢复为空完成。
                    .doOnError(result::tryEmitError)
                    // 不恢复的话，一个任务失败会让 concatMap 终止，后续同 Lane 任务都不会执行。
                    .onErrorResume(error -> Mono.empty())
                    // Lane 只关心任务何时结束，不需要继续传播数据库操作的业务值。
                    .then();
        }
    }
}
