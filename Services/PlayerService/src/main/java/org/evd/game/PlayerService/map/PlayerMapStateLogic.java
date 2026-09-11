package org.evd.game.PlayerService.map;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.PlayerService.dbDef.db.bean.DBRoleMapData;
import org.evd.game.PlayerService.dbDef.db.bean.DBTransferContext;
import org.evd.game.PlayerService.dbDef.db.table.DBRoleMapDataTable;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.runtime.Service;

/** 负责玩家地图和匹配状态的互斥进入、退出及超时清理。 */
@Actor
@Slf4j
public final class PlayerMapStateLogic {
    public boolean enter(long playerId, PlayerMapState nextState) {
        if (nextState == null || nextState == PlayerMapState.NONE) {
            throw new IllegalArgumentException("进入状态不能为空且不能是 NONE");
        }

        DBRoleMapData data = getOrCreate(playerId);
        PlayerMapState currentState = currentState(data);
        long currentMill = Service.getTime();
        if (currentState != PlayerMapState.NONE) {
            if (!currentState.isTimeout(data.getStateStartMill(), currentMill)) {
                log.warn("玩家已有地图状态，不能进入新状态: playerId={}, currentState={}, nextState={}, stateStartMill={}",
                        playerId, currentState, nextState, data.getStateStartMill());
                return false;
            }
            log.warn("玩家地图状态已超时，进入新状态前清理旧状态: playerId={}, state={}, stateStartMill={}, currentMill={}",
                    playerId, currentState, data.getStateStartMill(), currentMill);
            clear(data);
        }

        data.setStateType(nextState.getId());
        data.setStateStartMill(currentMill);
        return true;
    }

    public boolean exit(long playerId, PlayerMapState expectedState) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        if (data == null) {
            return false;
        }
        PlayerMapState currentState = currentState(data);
        if (currentState != expectedState) {
            log.warn("玩家退出地图状态失败，当前状态不匹配: playerId={}, expectedState={}, currentState={}",
                    playerId, expectedState, currentState);
            return false;
        }
        clearState(data);
        return true;
    }

    /** 将玩家从一个已知匹配状态原子切换到地图转场状态。 */
    public boolean transition(long playerId, PlayerMapState expectedState, PlayerMapState nextState) {
        if (expectedState == null || nextState == null || nextState == PlayerMapState.NONE) {
            throw new IllegalArgumentException("状态切换参数不能为空，且 nextState 不能是 NONE");
        }
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        PlayerMapState currentState = data == null ? PlayerMapState.NONE : currentState(data);
        if (currentState != expectedState) {
            log.warn("玩家状态切换失败，当前状态不匹配: playerId={}, expectedState={}, nextState={}, currentState={}",
                    playerId, expectedState, nextState, currentState);
            return false;
        }
        data.setStateType(nextState.getId());
        data.setStateStartMill(Service.getTime());
        return true;
    }

    public boolean isIn(long playerId, PlayerMapState expectedState) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        return data != null && currentState(data) == expectedState;
    }

    /** 检查并清理当前状态；返回被清理的状态，未超时返回 NONE。 */
    public PlayerMapState expire(long playerId, long currentMill) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        if (data == null) {
            return PlayerMapState.NONE;
        }
        PlayerMapState currentState = currentState(data);
        if (!currentState.isTimeout(data.getStateStartMill(), currentMill)) {
            return PlayerMapState.NONE;
        }
        long stateStartMill = data.getStateStartMill();
        clear(data);
        log.warn("玩家地图状态超时并已清理: playerId={}, state={}, stateStartMill={}, currentMill={}",
                playerId, currentState, stateStartMill, currentMill);
        return currentState;
    }

    public PlayerMapState getState(long playerId) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        return data == null ? PlayerMapState.NONE : currentState(data);
    }

    /** 返回当前互斥状态距离超时还剩多久；没有活动状态或已经超时返回 0。 */
    public long getRemainingMill(long playerId) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        if (data == null) {
            return 0L;
        }
        PlayerMapState state = currentState(data);
        long timeoutMill = state.getTimeoutMill();
        if (timeoutMill <= 0L || data.getStateStartMill() <= 0L) {
            return 0L;
        }
        long remainingMill = data.getStateStartMill() + timeoutMill - Service.getTime();
        return Math.max(remainingMill, 0L);
    }

    private PlayerMapState currentState(DBRoleMapData data) {
        return PlayerMapState.fromId(data.getStateType());
    }

    private void clear(DBRoleMapData data) {
        clearState(data);
        data.setTransferContext(new DBTransferContext());
    }

    private void clearState(DBRoleMapData data) {
        data.setStateType(PlayerMapState.NONE.getId());
        data.setStateStartMill(0L);
    }

    private DBRoleMapData getOrCreate(long playerId) {
        DBRoleMapData data = DBRoleMapDataTable.get(playerId);
        if (data != null) {
            return data;
        }
        data = new DBRoleMapData();
        data.setPlayerId(playerId);
        data.setStateType(PlayerMapState.NONE.getId());
        DBRoleMapDataTable.add(playerId, data, true);
        return data;
    }
}
