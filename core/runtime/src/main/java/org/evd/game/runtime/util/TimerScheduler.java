package org.evd.game.runtime.util;

import java.util.Collection;

/**
 * Service 线程内使用的定时调度契约。
 *
 * <p>对外暴露的 timerId 在调度器生命周期内单调分配且不会复用；实现可以使用优先队列或时间轮。</p>
 */
public interface TimerScheduler extends AutoCloseable {
    @FunctionalInterface
    public interface CallbackFailureHandler {
        void onFailure(long timerId, Throwable failure);
    }

    long scheduleDelay(long now, long delayMillis, Runnable callback);

    long scheduleAt(long deadline, Runnable callback);

    long scheduleRepeated(long now, long intervalMillis, boolean immediate, Runnable callback);

    boolean cancel(long timerId);

    int cancelAll(Collection<Long> timerIds);

    void update(long now);

    @Override
    void close();
}
