package org.evd.game.runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledExecutor extends ScheduledThreadPoolExecutor {
    private final String name;
    public ScheduledExecutor(String name, int corePoolSize) {
        super(corePoolSize, createThreadFactory(name));
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private static ThreadFactory createThreadFactory(String name) {
        ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        AtomicInteger threadIndex = new AtomicInteger(1);
        return runnable -> {
            Thread thread = defaultFactory.newThread(runnable);
            thread.setName(name + "-" + threadIndex.getAndIncrement());
            return thread;
        };
    }
}
