package org.evd.game.runtime.continuation;

import org.evd.game.runtime.CoroutineLockManager;

/**
 * @author zhuxiaoqing
 * @Description: ContinuationLockScope
 * @Date 2026/6/24 13:23
 **/
public class ContinuationLockScope implements AutoCloseable {
    private final Task.ContinuationWrapper continuation;
    private boolean closed;
    CoroutineLockManager coroutineLockManager;

    public ContinuationLockScope(CoroutineLockManager coroutineLockManager, Task.ContinuationWrapper continuation) {
        this.continuation = continuation;
        this.coroutineLockManager = coroutineLockManager;
    }

    @Override
    public void close() {
        if (closed || continuation == null) {
            return;
        }
        closed = true;
        coroutineLockManager.release(continuation);
    }
}
