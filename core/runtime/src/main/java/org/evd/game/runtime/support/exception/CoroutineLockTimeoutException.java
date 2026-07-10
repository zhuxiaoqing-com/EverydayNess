package org.evd.game.runtime.support.exception;

import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.support.RpcErrorCodes;

public class CoroutineLockTimeoutException extends SysException {
    private final String serviceId;
    private final LockType lockType;
    private final Object lockKey;
    private final long waitId;
    private final int timeoutMillis;

    public CoroutineLockTimeoutException(String serviceId, LockType lockType, Object lockKey, long waitId, int timeoutMillis) {
        super(RpcErrorCodes.COROUTINE_LOCK_TIMEOUT,
                "coroutine lock timeout: service={}, type={}, key={}, waitId={}, timeoutMillis={}",
                serviceId, lockType, lockKey, waitId, timeoutMillis);
        this.serviceId = serviceId;
        this.lockType = lockType;
        this.lockKey = lockKey;
        this.waitId = waitId;
        this.timeoutMillis = timeoutMillis;
    }

    public String getServiceId() {
        return serviceId;
    }

    public LockType getLockType() {
        return lockType;
    }

    public Object getLockKey() {
        return lockKey;
    }

    public long getWaitId() {
        return waitId;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }
}
