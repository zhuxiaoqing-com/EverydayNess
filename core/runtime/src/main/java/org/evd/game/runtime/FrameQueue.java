package org.evd.game.runtime;

import org.evd.game.runtime.support.LogCore;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * 队列按帧消费时使用的处理数量计算器。
 */
public final class FrameQueue<E> {
    private static final int DEFAULT_MAX_PROCESS_PER_FRAME = 1000;
    private static final int WARNING_THRESHOLD = 60_000;
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30L);

    private final Queue<E> queue;
    private final Deque<E> deque;
    private final int maxProcessPerFrame;
    private long lastWarningNanos;

    public FrameQueue(Queue<E> queue) {
        this(queue, DEFAULT_MAX_PROCESS_PER_FRAME);
    }

    public FrameQueue(Queue<E> queue, int maxProcessPerFrame) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.deque = toDeque(queue);
        if (maxProcessPerFrame <= 0) {
            throw new IllegalArgumentException("maxProcessPerFrame must be positive: " + maxProcessPerFrame);
        }
        this.maxProcessPerFrame = maxProcessPerFrame;
    }

    public boolean add(E element) {
        return queue.add(element);
    }

    public E poll() {
        return queue.poll();
    }

    public void addLast(E element) {
        requireDeque().addLast(element);
    }

    public E pollFirst() {
        return requireDeque().pollFirst();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }

    public List<E> snapshot() {
        return new ArrayList<>(queue);
    }

    /** 返回底层队列当前大小。 */
    public int size() {
        return queue.size();
    }

    /** 使用底层容器当前的 size 计算本帧处理数量。 */
    public int getFrameProcessNum() {
        return getFrameProcessNum(queue.size());
    }

    /**
     * 使用调用方提供的队列 size 计算本帧处理数量。
     * 适合容器已有准确计数或 size 获取成本较高的场景。
     */
    public int getFrameProcessNum(int queueSize) {
        if (queueSize < 0) {
            throw new IllegalArgumentException("queueSize must not be negative: " + queueSize);
        }
        if (queueSize > WARNING_THRESHOLD && shouldWarn()) {
            LogCore.core.warn("frame queue backlog exceeds threshold: queueType={}, size={}, threshold={}",
                    queue.getClass().getSimpleName(), queueSize, WARNING_THRESHOLD,
                    new Throwable("frame queue backlog stack"));
        }
        return Math.min(queueSize, maxProcessPerFrame);
    }

    private boolean shouldWarn() {
        long now = System.nanoTime();
        if (lastWarningNanos != 0L && now - lastWarningNanos < WARNING_INTERVAL_NANOS) {
            return false;
        }
        lastWarningNanos = now;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <E> Deque<E> toDeque(Queue<E> queue) {
        return queue instanceof Deque<?> value ? (Deque<E>) value : null;
    }

    private Deque<E> requireDeque() {
        if (deque == null) {
            throw new UnsupportedOperationException("underlying queue is not a Deque");
        }
        return deque;
    }
}
