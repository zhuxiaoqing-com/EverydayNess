package org.evd.game.runtime.continuation;

/**
 * @author zhuxiaoqing
 * @Description: ContinuationLockScope
 * @Date 2026/6/24 13:23
 **/
public class ContinuationLockScope implements AutoCloseable {
    private final CoroutineLockManager manager;
    private final Task.ContinuationWrapper owner;
    private final CoroutineLock lock;
    private boolean closed;

    ContinuationLockScope(CoroutineLockManager manager,
                          Task.ContinuationWrapper owner,
                          CoroutineLock lock) {
        this.manager = manager;
        this.owner = owner;
        this.lock = lock;
    }

    @Override
    public void close() {
        if (closed || manager == null || owner == null || lock == null) {
            return;
        }
        manager.release(owner, lock);
        closed = true;
    }
}
