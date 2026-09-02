package org.evd.game.runtime.Db.table;

import org.evd.game.base.DBException;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** 集中管理玩家 MDB 的 load、保留和 flush 生命周期。 */
final class MdbPlayerManager {
    private static final Logger logger = LoggerFactory.getLogger(MdbPlayerManager.class);
    private static final long TICK_INTERVAL_MILLIS = 10 * TimeUtils.SEC;
    private static final long FLUSH_DELAY_MILLIS = 20 * TimeUtils.MIN;

    private final Mdb mdb;
    private final Service service;
    private final Map<Long, MdbPlayerInfo> playerInfoMap = new HashMap<>();
    private final TimerState tickTimerState = new TimerState(TICK_INTERVAL_MILLIS);
    private Consumer<MdbPlayerInfo> playerCacheExpiredCallback;

    MdbPlayerManager(Mdb mdb, Service service) {
        this.mdb = mdb;
        this.service = service;
    }

    void load(long playerId, String userId) {
        MdbPlayerInfo info = getOrCreate(playerId, userId);
        info.setInUse(true);
        info.setFlushDeadline(0L);

        MdbState state = info.getState();
        if (state == MdbState.LOAD_FINISH || state == MdbState.FLUSH_WAIT) {
            // FLUSH_WAIT 还没有开始写库，可以直接复用当前 cache。
            if (state == MdbState.FLUSH_WAIT) {
                info.setState(MdbState.LOAD_FINISH);
            }
            return;
        }
        if (state == MdbState.EMPTY || state == MdbState.FLUSH_FINISH) {
            // flush 已经清理了 cache；WAIT 只表示本次 load 已申请，还没有真正开始。
            info.setState(MdbState.LOAD_WAIT);
        }

        try (ContinuationLockScope ignored = lifecycleLock(playerId)) {
            // 等待生命周期锁期间可能已经下线，或者该状态对象已经被 flush 清理。
            if (!info.isInUse() || playerInfoMap.get(playerId) != info) {
                return;
            }
            state = info.getState();
            switch (state) {
                // 数据已经完整加载并且仍在内存中，本次 load 直接复用现有数据。
                case LOAD_FINISH -> {
                    return;
                }
                // flush 还未真正开始，取消等待中的 flush，恢复为可用状态。
                case FLUSH_WAIT -> {
                    info.setState(MdbState.LOAD_FINISH);
                    return;
                }
                // 上一次 flush 已完成并清理了内存，本次需要申请新的 load。
                case FLUSH_FINISH -> info.setState(MdbState.LOAD_WAIT);
                // 当前没有完整 MDB 数据，本次需要申请新的 load。
                case EMPTY -> info.setState(MdbState.LOAD_WAIT);
                // 已有 load 请求排队，本次复用同一把锁并在锁内再次判断。
                case LOAD_WAIT -> {
                }
                // 拿锁后仍处于 LOAD_ING，说明已有 load 没有正确持有这把锁。
                case LOAD_ING -> throw lifecycleStateBug("load", info);
                // 拿锁后仍处于 FLUSH_ING，说明已有 flush 没有正确持有这把锁。
                case FLUSH_ING -> throw lifecycleStateBug("load", info);
            }
            loadLocked(info);
        }
    }

    void logout(long playerId) {
        MdbPlayerInfo info = playerInfoMap.get(playerId);
        if (info == null) {
            return;
        }
        info.setInUse(false);
        info.setFlushDeadline(Service.getTime() + FLUSH_DELAY_MILLIS);
        logger.info("玩家 MDB 进入延迟 flush: playerId={}, state={}, delayMillis={}",
                playerId, info.getState(), FLUSH_DELAY_MILLIS);
    }

    void checkAccess(long playerId, boolean forLoad) {
        MdbPlayerInfo info = playerInfoMap.get(playerId);
        MdbState state = info == null ? null : info.getState();
        boolean allowed = state == MdbState.LOAD_FINISH
                || (forLoad && state == MdbState.LOAD_ING);
        if (!allowed) {
            throw new DBException("玩家 MDB 当前不可访问: playerId=" + playerId
                    + ", state=" + state + ", forLoad=" + forLoad);
        }
    }

    private void loadLocked(MdbPlayerInfo info) {
        if (info.getState() != MdbState.LOAD_WAIT) {
            return;
        }
        info.setState(MdbState.LOAD_ING);
        try {
            mdb.loadPlayerAllTableToMemoryInternal(info.getPlayerId());
        } catch (RuntimeException e) {
            failLoad(info);
            throw new DBException("玩家 MDB LOAD_FAILED: playerId=" + info.getPlayerId(), e);
        }

        if (info.getState() != MdbState.LOAD_ING) {
            return;
        }
        info.setState(MdbState.LOAD_FINISH);
    }

    private void flushLocked(MdbPlayerInfo info) {
        if (info.getState() != MdbState.FLUSH_WAIT) {
            return;
        }
        info.setState(MdbState.FLUSH_ING);
        boolean success;
        try {
            success = mdb.flushPlayerAllTableToMemory(info.getPlayerId());
        } catch (RuntimeException e) {
            if (info.getState() == MdbState.FLUSH_ING) {
                info.setState(MdbState.LOAD_FINISH);
            }
            throw e;
        }
        if (info.getState() != MdbState.FLUSH_ING) {
            return;
        }
        if (!success) {
            // flush 已经结束但没有完成落库，保留 cache 并等待下一次明确重试。
            info.setState(MdbState.LOAD_FINISH);
            if (!info.isInUse()) {
                info.setFlushDeadline(Service.getTime() + FLUSH_DELAY_MILLIS);
            }
            return;
        }

        if (info.isInUse()) {
            // 登录请求正在等待同一把锁，交给它在锁内转入 LOAD_WAIT。
            info.setState(MdbState.EMPTY);
            return;
        }
        if (!mdb.clearPlayerCacheIfClean(info.getPlayerId())) {
            info.setState(MdbState.LOAD_FINISH);
            info.setFlushDeadline(Service.getTime() + FLUSH_DELAY_MILLIS);
            return;
        }
        info.setState(MdbState.EMPTY);
        if (playerInfoMap.remove(info.getPlayerId(), info) && playerCacheExpiredCallback != null) {
            playerCacheExpiredCallback.accept(info);
        }
    }

    void tick(long currentTime) {
        if (!tickTimerState.canExecute(currentTime)) {
            return;
        }
        tickTimerState.markStart();
        service.launchCoroutine(() -> {
            try {
                tickCoroutine(Service.getTime());
            } finally {
                tickTimerState.markComplete();
            }
        });
    }

    private void tickCoroutine(long currentTime) {
        List<MdbPlayerInfo> infos = new ArrayList<>(playerInfoMap.values());
        for (MdbPlayerInfo info : infos) {
            if (playerInfoMap.get(info.getPlayerId()) != info
                    || info.isInUse() || info.getFlushDeadline() <= 0L
                    || currentTime < info.getFlushDeadline()) {
                continue;
            }
            if (info.getState() == MdbState.LOAD_FINISH) {
                info.setState(MdbState.FLUSH_WAIT);
            }

            try (ContinuationLockScope ignored = lifecycleLock(info.getPlayerId())) {
                MdbState state = info.getState();
                switch (state) {
                    // flush 已经申请且仍在等待，确认玩家仍离线后开始真正 flush。
                    case FLUSH_WAIT -> {
                        if (!info.isInUse()) {
                            flushLocked(info);
                        }
                    }
                    // 处于稳定可用状态但已到 flush 时间，先转 WAIT 再开始 flush。
                    case LOAD_FINISH -> {
                        if (!info.isInUse()) {
                            info.setState(MdbState.FLUSH_WAIT);
                            flushLocked(info);
                        }
                    }
                    // 没有 MDB 数据，不需要执行 flush，等待后续 load 或清理状态。
                    case EMPTY -> {
                    }
                    // flush 已经完成，本次 tick 不重复处理。
                    case FLUSH_FINISH -> {
                    }
                    // load 尚未真正开始，不能由 flush 抢先处理。
                    case LOAD_WAIT -> {
                    }
                    // 拿锁后仍处于 LOAD_ING，说明已有 load 没有正确持有这把锁。
                    case LOAD_ING -> throw lifecycleStateBug("flush", info);
                    // 拿锁后仍处于 FLUSH_ING，说明已有 flush 没有正确持有这把锁。
                    case FLUSH_ING -> throw lifecycleStateBug("flush", info);
                }
            }
        }
    }

    private IllegalStateException lifecycleStateBug(String operation, MdbPlayerInfo info) {
        return new IllegalStateException("MDB 生命周期锁与状态不一致: operation=" + operation
                + ", playerId=" + info.getPlayerId() + ", state=" + info.getState());
    }

    private void failLoad(MdbPlayerInfo info) {
        // 先离开 LOAD_ING，再清理已经加载的半套 cache；后续回调会被状态门禁拦截。
        if (info.getState() == MdbState.LOAD_ING) {
            info.setState(MdbState.EMPTY);
        }
        mdb.clearPlayerCache(info.getPlayerId());
    }

    private ContinuationLockScope lifecycleLock(long playerId) {
        return service.awaitCoroutineLockScope(LockType.MDB_PLAYER, playerId);
    }

    private MdbPlayerInfo getOrCreate(long playerId, String userId) {
        if (playerId <= 0L) {
            throw new DBException("playerId 非法: " + playerId);
        }
        if (userId == null || userId.isBlank()) {
            throw new DBException("玩家 MDB 加载 userId 非法: playerId=" + playerId);
        }
        MdbPlayerInfo info = playerInfoMap.get(playerId);
        if (info != null) {
            if (!userId.equals(info.getUserId())) {
                throw new DBException("玩家 MDB userId 不一致: playerId=" + playerId
                        + ", currentUserId=" + info.getUserId() + ", requestUserId=" + userId);
            }
            return info;
        }
        MdbPlayerInfo created = new MdbPlayerInfo(playerId, userId);
        MdbPlayerInfo existing = playerInfoMap.putIfAbsent(playerId, created);
        if (existing != null && !userId.equals(existing.getUserId())) {
            throw new DBException("玩家 MDB userId 不一致: playerId=" + playerId
                    + ", currentUserId=" + existing.getUserId() + ", requestUserId=" + userId);
        }
        return existing == null ? created : existing;
    }

    void setPlayerCacheExpiredCallback(Consumer<MdbPlayerInfo> callback) {
        this.playerCacheExpiredCallback = Objects.requireNonNull(callback, "callback");
    }

    List<String> getPlayerUserIds() {
        List<String> userIds = new ArrayList<>(playerInfoMap.size());
        for (MdbPlayerInfo info : playerInfoMap.values()) {
            userIds.add(info.getUserId());
        }
        return userIds;
    }

    void close() {
        playerInfoMap.clear();
    }
}
