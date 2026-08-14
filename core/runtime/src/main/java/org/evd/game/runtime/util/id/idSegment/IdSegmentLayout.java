package org.evd.game.runtime.util.id.idSegment;

import org.evd.game.runtime.util.id.IDEnum;
import org.evd.game.runtime.util.id.IdLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

/** ID 号段布局族，具体位宽由版本实现声明。 */
public abstract class IdSegmentLayout extends IdLayout {

    private static final Logger log = LoggerFactory.getLogger(IdSegmentLayout.class);
    public static final int VERSION = 0;
    public static final int VERSION_BITS = 1;
    private static final int SEGMENT_SIZE = 10_000;
    private static final int PREFETCH_THRESHOLD = 2_000;

    private final IdSegmentAllocator segmentAllocator;
    private final Map<IDEnum, SegmentState> states = new EnumMap<>(IDEnum.class);
    private final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mysql-id-prefetch");
        thread.setDaemon(true);
        return thread;
    });

    private static final class VersionLayouts {
        private static final Map<Integer, LayoutFactory> FACTORY_MAP = Map.of(
                VERSION, IdSegmentLayout0::new);
    }

    protected IdSegmentLayout(int platformBits, int playerServerBits, int nodeBits,
                              int incrementBits, int platformId, int playerServerId,
                              int nodeId) {
        super(1, VERSION, platformBits, playerServerBits, nodeBits, incrementBits,
                platformId, playerServerId, nodeId);
        segmentAllocator = new MysqlIdSegmentAllocator();
        for (IDEnum idEnum : IDEnum.values()) {
            states.put(idEnum, new SegmentState(idEnum));
        }
    }

    /** 创建指定版本的 ID 号段布局。 */
    public static IdSegmentLayout create(int version, int platformId, int playerServerId, int nodeId) {
        LayoutFactory factory = VersionLayouts.FACTORY_MAP.get(version);
        if (factory == null) {
            throw new IllegalArgumentException("unsupported mysql id layout version: " + version);
        }
        return factory.create(platformId, playerServerId, nodeId);
    }

    @Override
    protected final long nextIncrementId(IDEnum idEnum) {
        SegmentState state = states.get(idEnum);
        if (state == null) {
            throw new IllegalStateException("mysql id enum state is not initialized: " + idEnum);
        }
        return state.next();
    }

    @Override
    public IdSegmentLayout find(long id) {
        return matches(id) ? this : null;
    }

    /** 关闭当前布局的预取线程和号段申请连接。 */
    public final void close() {
        prefetchExecutor.shutdownNow();
        states.values().forEach(SegmentState::logRemainingOnClose);
        segmentAllocator.close();
    }

    @FunctionalInterface
    private interface LayoutFactory {
        IdSegmentLayout create(int platformId, int playerServerId, int nodeId);
    }

    private final class SegmentState {
        private final IDEnum idEnum;
        private volatile IdSegment current;
        // 当前号段快用完时，提前在后台申请下一个号段；申请完成后 future 中保存 IdSegment。
        private volatile CompletableFuture<IdSegment> prefetched;

        private SegmentState(IDEnum idEnum) {
            this.idEnum = idEnum;
        }

        private long next() {
            for (;;) {
                IdSegment segment = current;
                if (segment == null) {
                    synchronized (this) {
                        if (current == null) {
                            log.info("ID 号段首次使用，开始申请号段: idEnum={}, segmentSize={}, "
                                            + "prefetchThreshold={}",
                                    idEnum, SEGMENT_SIZE, PREFETCH_THRESHOLD);
                            current = reserveSegment("首次访问");
                        }
                    }
                    continue;
                }

                long value = segment.current.getAndIncrement();
                if (value <= segment.end) {
                    // 剩余数量达到阈值后，只提前申请，不切换当前号段，当前号段仍继续发号。
                    if (segment.end - value + 1 <= PREFETCH_THRESHOLD) {
                        startPrefetch();
                    }
                    return value;
                }

                synchronized (this) {
                    if (current != segment) {
                        continue;
                    }
                    log.warn("ID 号段已耗尽: idEnum={}, start={}, end={}, segmentSize={}",
                            idEnum, segment.start, segment.end, SEGMENT_SIZE);
                    CompletableFuture<IdSegment> future = prefetched;
                    prefetched = null;
                    if (future == null) {
                        log.warn("ID 号段没有可用的预取号段，开始同步申请: idEnum={}", idEnum);
                        current = reserveSegment("号段耗尽后同步申请");
                        continue;
                    }

                    // 必须在 join 前记录状态，不能用 waitMillis == 0 判断是否等待过，
                    // 因为实际等待时间可能不足 1 毫秒。
                    boolean prefetchedDoneBeforeJoin = future.isDone();
                    long waitStartNanos = System.nanoTime();
                    if (!prefetchedDoneBeforeJoin) {
                        log.warn("ID 号段预取尚未申请完成，开始等待: idEnum={}", idEnum);
                    }
                    try {
                        current = future.join();
                        long waitMillis = TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - waitStartNanos);
                        if (prefetchedDoneBeforeJoin) {
                            log.info("ID 号段预取已申请完成，直接切换: idEnum={}, start={}, end={}",
                                    idEnum, current.start, current.end);
                        } else {
                            log.warn("ID 号段预取等待完成并切换: idEnum={}, start={}, end={}, "
                                            + "waitMs={}",
                                    idEnum, current.start, current.end, waitMillis);
                        }
                    } catch (RuntimeException e) {
                        long waitMillis = TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - waitStartNanos);
                        log.error("ID 号段预取申请失败: idEnum={}, waitMs={}", idEnum, waitMillis, e);
                        throw e;
                    }
                }
            }
        }

        private IdSegment reserveSegment(String reason) {
            long startNanos = System.nanoTime();
            long start = segmentAllocator.reserveStart(idEnum, SEGMENT_SIZE, maxIncrementId());
            IdSegment segment = new IdSegment(start, start + SEGMENT_SIZE - 1);
            long costMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.info("ID 号段申请完成: idEnum={}, reason={}, start={}, end={}, size={}, costMs={}",
                    idEnum, reason, segment.start, segment.end, SEGMENT_SIZE, costMillis);
            return segment;
        }

        private void startPrefetch() {
            if (prefetched != null) {
                return;
            }
            synchronized (this) {
                if (prefetched == null) {
                    long remaining = current == null ? 0 : current.remaining();
                    // 异步执行数据库申请，让当前号段的剩余 ID 先继续对外发放。
                    // 这里保存的是申请任务，不是立即切换 current；切换发生在当前号段耗尽时。
                    log.info("ID 号段剩余约 20%，开始异步预取: idEnum={}, start={}, end={}, "
                                    + "remaining={}, segmentSize={}, threshold={}",
                            idEnum, current.start, current.end, remaining, SEGMENT_SIZE, PREFETCH_THRESHOLD);
                    prefetched = CompletableFuture.supplyAsync(
                            () -> reserveSegment("剩余 20% 异步预取"), prefetchExecutor);
                }
            }
        }

        private void logRemainingOnClose() {
            IdSegment segment = current;
            CompletableFuture<IdSegment> future = prefetched;
            if (segment == null) {
                log.info("ID 号段布局关闭时尚未申请号段: idEnum={}, segmentSize={}",
                        idEnum, SEGMENT_SIZE);
                return;
            }
            log.info("ID 号段布局关闭时号段统计: idEnum={}, start={}, end={}, remaining={}, "
                            + "segmentSize={}, prefetched={}, prefetchDone={}",
                    idEnum, segment.start, segment.end, segment.remaining(), SEGMENT_SIZE,
                    future != null, future != null && future.isDone());
        }
    }

    /** 布局层持有的号段范围和当前发号位置。 */
    static final class IdSegment {
        private final long start;
        private final long end;
        private final AtomicLong current;

        IdSegment(long start, long end) {
            this.start = start;
            this.end = end;
            this.current = new AtomicLong(start);
        }

        private long remaining() {
            return Math.max(0, end - current.get() + 1);
        }
    }
}
