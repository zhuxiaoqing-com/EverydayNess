package org.evd.game.runtime.util.id.idSegment;

import org.evd.game.runtime.util.id.IDEnum;

import java.util.Objects;

/**
 * ID 号段申请器抽象，负责约束所有号段申请实现的公共参数。
 *
 * <p>具体的数据来源由子类实现，例如 MySQL 或后续的 IDService。</p>
 */
public abstract class IdSegmentAllocator {

    /**
     * 申请一个号段的起点。号段范围为 {@code [start, start + segmentSize - 1]}。
     */
    public final long reserveStart(IDEnum idEnum, int segmentSize, long maxIncrementId) {
        Objects.requireNonNull(idEnum, "idEnum");
        if (segmentSize <= 0) {
            throw new IllegalArgumentException("segmentSize must be positive: " + segmentSize);
        }
        if (maxIncrementId < segmentSize - 1) {
            throw new IllegalArgumentException("maxIncrementId is too small: " + maxIncrementId);
        }
        return doReserveStart(idEnum, segmentSize, maxIncrementId);
    }

    protected abstract long doReserveStart(IDEnum idEnum, int segmentSize, long maxIncrementId);

    public abstract void close();
}
