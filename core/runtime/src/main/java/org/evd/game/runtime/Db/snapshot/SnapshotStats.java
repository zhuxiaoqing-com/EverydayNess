package org.evd.game.runtime.Db.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Node 级快照序列化窗口统计；每次写入只更新低竞争的原子计数器。 */
public final class SnapshotStats {
    /** 输出快照统计日志的日志器。 */
    private static final Logger logger = LoggerFactory.getLogger(SnapshotStats.class);

    /** 一个 KiB 对应的字节数。 */
    private static final long KB = 1024L;
    /** 一个 MiB 对应的字节数。 */
    private static final long MB = 1024L * KB;
    /** 大小桶的包含式上界；最后一个桶统计超过 1 MiB 的记录。 */
    private static final long[] BUCKET_LIMITS = {
            KB, 4 * KB, 16 * KB, 64 * KB, 256 * KB, MB
    };

    /** 当前 Node 所有快照实体的汇总计数器。 */
    private final Counters node = new Counters();
    /** 按 serviceId 保存的服务级及 DB 类级计数器。 */
    private final ConcurrentMap<String, ServiceCounters> services = new ConcurrentHashMap<>();

    /**
     * 记录一次 DB 实体 Protostuff 序列化的实际字节数。
     *
     * @param serviceId 执行快照序列化的 Service 标识
     * @param dbClass   被序列化的 DB 实体类
     * @param serializedBytes Protostuff 返回的序列化字节数
     * @throws IllegalArgumentException 字节数为负数时抛出
     */
    public void record(String serviceId, Class<?> dbClass, int serializedBytes) {
        if (serializedBytes < 0) {
            throw new IllegalArgumentException("serializedBytes 不能为负数: " + serializedBytes);
        }
        String dbClassName = dbClass.getName();
        node.record(serializedBytes);
        ServiceCounters service = services.computeIfAbsent(serviceId, ignored -> new ServiceCounters());
        service.record(dbClassName, serializedBytes);
    }

    /**
     * 输出并重置 Node、Service 和 DB 类的统计窗口，同时记录 Map 使用量。
     *
     * @param nodeId 当前 Node 标识
     * @param file 统计数据所属的 Chronicle Map 文件
     * @param mapSize Chronicle Map 当前条目数
     * @param configuredEntries Chronicle Map 配置容量
     */
    public void logAndReset(int nodeId, Path file, long mapSize, long configuredEntries) {
        WindowSnapshot nodeSnapshot = node.drain();
        logger.info("[SnapshotStats] window=5m, nodeId={}, file={}, serializeCount={}, totalSerializedBytes={}, "
                        + "averageSerializedBytes={}, maxSerializedBytes={}, minSerializedBytes={}, {}",
                nodeId, file, nodeSnapshot.serializeCount, nodeSnapshot.totalSerializedBytes,
                nodeSnapshot.averageSerializedBytes(), nodeSnapshot.maxSerializedBytes,
                nodeSnapshot.minSerializedBytes, bucketFields(nodeSnapshot));

        for (Map.Entry<String, ServiceCounters> serviceEntry : services.entrySet()) {
            WindowSnapshot serviceSnapshot = serviceEntry.getValue().drain();
            if (serviceSnapshot.serializeCount > 0) {
                logger.info("[SnapshotStats-Service] window=5m, file={}, serviceId={}, serializeCount={}, "
                                + "totalSerializedBytes={}, averageSerializedBytes={}, maxSerializedBytes={}, {}",
                        file, serviceEntry.getKey(), serviceSnapshot.serializeCount,
                        serviceSnapshot.totalSerializedBytes, serviceSnapshot.averageSerializedBytes(),
                        serviceSnapshot.maxSerializedBytes, bucketFields(serviceSnapshot));
            }
            for (Map.Entry<String, Counters> dbEntry : serviceEntry.getValue().dbClasses.entrySet()) {
                WindowSnapshot dbSnapshot = dbEntry.getValue().drain();
                if (dbSnapshot.serializeCount > 0) {
                    logger.info("[SnapshotStats-DB] window=5m, file={}, serviceId={}, dbClass={}, serializeCount={}, "
                                    + "averageSerializedBytes={}, maxSerializedBytes={}, {}",
                            file, serviceEntry.getKey(), dbEntry.getKey(), dbSnapshot.serializeCount,
                            dbSnapshot.averageSerializedBytes(), dbSnapshot.maxSerializedBytes,
                            bucketFields(dbSnapshot));
                }
            }
        }

        double usagePercent = configuredEntries <= 0L
                ? 0.0d
                : mapSize * 100.0d / configuredEntries;
        logger.info("[SnapshotStats-Map] nodeId={}, file={}, currentEntries={}, configuredEntries={}, "
                        + "capacityUsagePercent={}",
                nodeId, file, mapSize, configuredEntries, usagePercent);
    }

    /** 将各大小桶的数量格式化成日志字段。 */
    private static String bucketFields(WindowSnapshot snapshot) {
        long[] buckets = snapshot.sizeBuckets;
        return "sizeBelow1KB=" + buckets[0]
                + ", size1KBTo4KB=" + buckets[1]
                + ", size4KBTo16KB=" + buckets[2]
                + ", size16KBTo64KB=" + buckets[3]
                + ", size64KBTo256KB=" + buckets[4]
                + ", size256KBTo1MB=" + buckets[5]
                + ", sizeAbove1MB=" + buckets[6];
    }

    /** 维护一个 Service 的窗口汇总和各 DB 类统计。 */
    private static final class ServiceCounters {
        /** 当前 Service 所有 DB 类的汇总计数器。 */
        private final Counters window = new Counters();
        /** 按 DB 实体类全限定名保存的计数器。 */
        private final ConcurrentMap<String, Counters> dbClasses = new ConcurrentHashMap<>();

        /** 同时更新 Service 汇总及指定 DB 类的序列化统计。 */
        private void record(String dbClassName, int bytes) {
            window.record(bytes);
            dbClasses.computeIfAbsent(dbClassName, ignored -> new Counters()).record(bytes);
        }

        /** 读取并重置 Service 汇总窗口。 */
        private WindowSnapshot drain() {
            return window.drain();
        }
    }

    /** 以低竞争原子计数器维护一个统计范围的序列化指标。 */
    private static final class Counters {
        /** 统计窗口内的序列化次数。 */
        private final LongAdder serializeCount = new LongAdder();
        /** 统计窗口内所有序列化结果的字节总数。 */
        private final LongAdder totalSerializedBytes = new LongAdder();
        /** 统计窗口内观察到的最大序列化字节数。 */
        private final LongAccumulator maxSerializedBytes = new LongAccumulator(Long::max, 0L);
        /** 统计窗口内观察到的最小序列化字节数。 */
        private final LongAccumulator minSerializedBytes = new LongAccumulator(Long::min, Long.MAX_VALUE);
        /** 按 BUCKET_LIMITS 划分的各大小桶计数器，末尾桶表示超过最大上界。 */
        private final LongAdder[] sizeBuckets = new LongAdder[BUCKET_LIMITS.length + 1];

        /** 创建计数器，并初始化所有序列化大小桶。 */
        private Counters() {
            for (int i = 0; i < sizeBuckets.length; i++) {
                sizeBuckets[i] = new LongAdder();
            }
        }

        /** 将一次序列化结果计入次数、字节总量、最值和大小桶。 */
        private void record(int bytes) {
            serializeCount.increment();
            totalSerializedBytes.add(bytes);
            maxSerializedBytes.accumulate(bytes);
            minSerializedBytes.accumulate(bytes);
            sizeBuckets[bucketFor(bytes)].increment();
        }

        /** 读取并清零当前统计窗口；窗口没有记录时最值归零。 */
        private WindowSnapshot drain() {
            long count = serializeCount.sumThenReset();
            long totalBytes = totalSerializedBytes.sumThenReset();
            long maxBytes = maxSerializedBytes.getThenReset();
            long minBytes = minSerializedBytes.getThenReset();
            long[] buckets = new long[sizeBuckets.length];
            for (int i = 0; i < sizeBuckets.length; i++) {
                buckets[i] = sizeBuckets[i].sumThenReset();
            }
            if (count == 0L) {
                minBytes = 0L;
                maxBytes = 0L;
            }
            return new WindowSnapshot(count, totalBytes, minBytes, maxBytes, buckets);
        }

        /** 返回字节数对应的大小桶下标；上界值归入该上界对应的桶。 */
        private static int bucketFor(long bytes) {
            for (int i = 0; i < BUCKET_LIMITS.length; i++) {
                if (bytes <= BUCKET_LIMITS[i]) {
                    return i;
                }
            }
            return BUCKET_LIMITS.length;
        }
    }

    /**
     * 一次统计窗口读取并清零后的不可变计数快照。
     *
     * @param serializeCount 窗口内的序列化次数
     * @param totalSerializedBytes 窗口内序列化字节总数
     * @param minSerializedBytes 窗口内最小序列化字节数
     * @param maxSerializedBytes 窗口内最大序列化字节数
     * @param sizeBuckets 各序列化大小桶的记录数
     */
    private record WindowSnapshot(long serializeCount, long totalSerializedBytes,
                                  long minSerializedBytes, long maxSerializedBytes,
                                  long[] sizeBuckets) {
        /** 计算窗口内每次序列化的平均字节数；空窗口返回 0。 */
        private double averageSerializedBytes() {
            return serializeCount == 0L ? 0.0d : (double) totalSerializedBytes / serializeCount;
        }
    }
}
