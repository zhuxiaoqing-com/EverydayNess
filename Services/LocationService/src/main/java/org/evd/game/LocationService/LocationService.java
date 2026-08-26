package org.evd.game.LocationService;

import org.evd.game.annotation.actor.RpcService;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

@RpcService(LocationInterface.class)
public class LocationService extends Service {
    private static final class LocationEntry {
        private final ActorAddress actorAddress;

        private LocationEntry(ActorAddress actorAddress) {
            this.actorAddress = new ActorAddress(actorAddress);
        }
    }


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

    private final Map<ActorId, LocationEntry> actorLocations = new HashMap<>();
    private final Map<ActorId, LockInfo> lockInfos = new HashMap<>();
    private long nextLockRevision = 1L;

    public LocationService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    public void add(ActorId actorId, ActorAddress actorAddress) {
        addNow(actorId, actorAddress);
    }

    public void remove(ActorId actorId, ActorAddress expectedActorAddress) {
        removeNow(actorId, expectedActorAddress);
    }

    public void lock(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        if (actorId == null || oldActorAddress == null) {
            return;
        }
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            lockNow(actorId, oldActorAddress, timeMillis);
        }
    }

    public void unlock(ActorId actorId, ActorAddress oldActorAddress, ActorAddress newActorAddress) {
        if (actorId == null) {
            return;
        }

        LockInfo lockInfo = lockInfos.get(actorId);
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
            actorLocations.remove(actorId);
        } else {
            actorLocations.put(actorId, new LocationEntry(newActorAddress));
        }
        lockInfos.remove(actorId);

        LogCore.core.info("LocationService 解锁actor: actorId={}, oldAddress={}, newAddress={}",
                actorId, oldActorAddress, newActorAddress);
        resumeContinuation(lockInfo.lockContinuation, null);
    }

    public ActorAddress get(ActorId actorId) {
        try (ContinuationLockScope ignored = awaitLocationLockScope(actorId)) {
            return getNow(actorId);
        }
    }

    private void addNow(ActorId actorId, ActorAddress actorAddress) {
        if (actorId == null || actorAddress == null) {
            LogCore.core.error("LocationService 添加 actor 参数非法: actorId={}, address={}",
                    actorId, actorAddress);
            return;
        }
        LocationEntry current = actorLocations.get(actorId);
        if (current != null) {
         /*   LogCore.core.error("LocationService 添加 actor 冲突，拒绝覆盖已有地址: actorId={}, address={}, currentAddress={}",
                    actorId, actorAddress, current.actorAddress);
            return false;*/
            LogCore.core.warn("LocationService 添加 actor 冲突，已有地址: actorId={}, address={}, currentAddress={}",
                    actorId, actorAddress, current.actorAddress);
        }
        actorLocations.put(actorId, new LocationEntry(actorAddress));
        LogCore.core.info("LocationService 添加actor: actorId={}, address={}", actorId, actorAddress);
    }

    private void removeNow(ActorId actorId, ActorAddress expectedActorAddress) {
        if (actorId == null || expectedActorAddress == null) {
            LogCore.core.error("LocationService 移除 actor 参数非法: actorId={}, expectedAddress={}",
                    actorId, expectedActorAddress);
            return;
        }
        LocationEntry current = actorLocations.get(actorId);
        if (current == null) {
            LogCore.core.warn("LocationService 移除 actor 失败，地址不存在: actorId={}, expectedAddress={}",
                    actorId, expectedActorAddress);
            return;
        }
        if (!current.actorAddress.equals(expectedActorAddress)) {
            LogCore.core.error("LocationService 移除 actor 冲突，地址不匹配: actorId={}, expectedAddress={}, currentAddress={}",
                    actorId, expectedActorAddress, current.actorAddress);
            return;
        }
        actorLocations.remove(actorId);
        LogCore.core.info("LocationService 移除actor: actorId={}, address={}", actorId, expectedActorAddress);
    }

    private void lockNow(ActorId actorId, ActorAddress oldActorAddress, int timeMillis) {
        Task.ContinuationWrapper lockContinuation = currentContinuation();
        lockContinuation.prepareWait();
        long revision = nextLockRevision++;
        long timerId = timeMillis > 0
                ? newOnceTimer(timeMillis, () -> onLockTimeout(actorId, revision))
                : 0L;

        lockInfos.put(actorId, new LockInfo(
                oldActorAddress, lockContinuation, revision, timerId));
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
        LocationEntry entry = actorLocations.get(actorId);
        return entry == null ? null : new ActorAddress(entry.actorAddress);
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
