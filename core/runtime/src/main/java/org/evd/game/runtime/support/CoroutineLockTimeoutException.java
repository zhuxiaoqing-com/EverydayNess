package org.evd.game.runtime.support;

public class CoroutineLockTimeoutException extends SysException {
    private final String serviceId;
    private final int lockType;
    private final Object lockKey;
    private final long waitId;
    private final int timeoutMillis;

    public CoroutineLockTimeoutException(String serviceId, int lockType, Object lockKey, long waitId, int timeoutMillis) {
        super("coroutine lock timeout: service={}, type={}, key={}, waitId={}, timeoutMillis={}",
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

    public int getLockType() {
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
