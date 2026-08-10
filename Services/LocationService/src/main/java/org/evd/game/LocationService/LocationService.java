package org.evd.game.LocationService;

import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcService;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

@RpcService(LocationInterface.class)
public class LocationService extends Service {

    private static final class LockInfo {
        private final ActorAddress lockActorAddress;
        private final Task.ContinuationWrapper lockContinuation;
        private final long revision;
        private final long timerId;

        private LockInfo(ActorAddress lockActorAddress,
                         Task.ContinuationWrapper lockContinuation,
                         long revision,
                         long timerId) {
            this.lockActorAddress = lockActorAddress;
            this.lockContinuation = lockContinuation;
            this.revision = revision;
            this.timerId = timerId;
        }
    }

    private final Map<ActorId, ActorAddress> actorLocations = new HashMap<>();
    private final Map<ActorId, LockInfo> lockInfos = new HashMap<>();
    private long nextLockRevision = 1L;

    public LocationService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Rpc
    public void add(ActorId actorId, ActorAddress actorAddress) {
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            addNow(actorId, actorAddress);
        }
    }

    @Rpc
    public void remove(ActorId actorId) {
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            removeNow(actorId);
        }
    }

    @Rpc
    public void lock(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        if (actorId == null || oldActorAddress == null) {
            return;
        }
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            lockNow(actorId, oldActorAddress, timeMillis);
        }
    }

    @Rpc
    public void unlock(ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress) {
        if (actorId == null) {
            return;
        }

        ActorId key = actorId;
        LockInfo lockInfo = lockInfos.get(key);
        if (lockInfo == null) {
            LogCore.core.error("LocationService 解锁失败，未找到锁: actorId={}, oldAddress={}",
                    actorId, oldActorAddress);
            return;
        }
        if (!lockInfo.lockActorAddress.equals(oldActorAddress)) {
            LogCore.core.error("LocationService 解锁失败，锁宿主不匹配: actorId={}, oldAddress={}, lockAddress={}",
                    actorId, oldActorAddress, lockInfo.lockActorAddress);
            return;
        }

        if (lockInfo.timerId != 0L) {
            removeTimer(lockInfo.timerId);
        }

        if (newActorAddress == null) {
            actorLocations.remove(key);
        } else {
            actorLocations.put(key, newActorAddress);
        }
        lockInfos.remove(key);

        LogCore.core.info("LocationService 解锁actor: actorId={}, oldAddress={}, newAddress={}",
                actorId, oldActorAddress, newActorAddress);
        resumeContinuation(lockInfo.lockContinuation, null);
    }

    @Rpc
    public ActorAddress get(ActorId actorId) {
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            return getNow(actorId);
        }
    }

    private void addNow(ActorId actorId, ActorAddress actorAddress) {
        if (actorId == null || actorAddress == null) {
            return;
        }
        actorLocations.put(actorId, actorAddress);
        LogCore.core.info("LocationService 添加actor: actorId={}, address={}",
                actorId, actorAddress);
    }

    private void removeNow(ActorId actorId) {
        if (actorId == null) {
            return;
        }
        actorLocations.remove(actorId);
        LogCore.core.info("LocationService 移除actor: actorId={}", actorId);
    }

    private void lockNow(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        ActorId key = actorId;
        ActorAddress lockActorAddress = oldActorAddress;
        Task.ContinuationWrapper lockContinuation = currentContinuation();
        lockContinuation.prepareWait();
        long revision = nextLockRevision++;
        long timerId = timeMillis > 0
                ? newOnceTimer(timeMillis, () -> onLockTimeout(key, revision))
                : 0L;

        lockInfos.put(key, new LockInfo(
                lockActorAddress, lockContinuation, revision, timerId));
        LogCore.core.info("LocationService 锁定actor: actorId={}, address={}, timeMillis={}",
                actorId, oldActorAddress, timeMillis);
        lockContinuation.markWaiting(
                new ContinuationDebugInfo.LocationLockWaitDebugInfo(actorId, oldActorAddress, timeMillis));
        lockContinuation.waitResult();
    }

    private ActorAddress getNow(ActorId actorId) {
        if (actorId == null) {
            return null;
        }
        ActorAddress actorAddress = actorLocations.get(actorId);
        return actorAddress == null ? null : new ActorAddress(actorAddress);
    }

    private void onLockTimeout(ActorId actorId, long revision) {
        LockInfo lockInfo = lockInfos.get(actorId);
        if (lockInfo == null || lockInfo.revision != revision) {
            return;
        }

        LogCore.core.info("LocationService 锁超时释放: actorId={}, address={}",
                actorId, lockInfo.lockActorAddress);
        unlock(actorId, lockInfo.lockActorAddress, lockInfo.lockActorAddress);
    }

    private ContinuationLockScope awaitLocationLockScope(ActorId actorId) {
        return awaitCoroutineLockScope(LockType.LOCATION, actorId);
    }


    @Override
    protected boolean supportLocation() {
        return false;
    }

}
