package org.evd.game.PlayerService.map;

import java.util.HashMap;
import java.util.Map;

/** 玩家地图和匹配流程状态；数据库只保存固定的状态 ID。 */
public enum PlayerMapState {
    NONE(0, 0L),
    ENTER_MAP(1, 30_000L),
    MATCHING(2, 60_000L),
    TEAM_MATCHING(3, 60_000L),
    TEAM_ENTER_MAP(4, 30_000L);

    private static final Map<Integer, PlayerMapState> BY_ID = createById();

    private final int id;
    private final long timeoutMill;

    PlayerMapState(int id, long timeoutMill) {
        this.id = id;
        this.timeoutMill = timeoutMill;
    }

    public int getId() {
        return id;
    }

    public long getTimeoutMill() {
        return timeoutMill;
    }

    public boolean isTimeout(long stateStartMill, long currentMill) {
        return getTimeoutMill() > 0L && stateStartMill > 0L
                && currentMill - stateStartMill >= getTimeoutMill();
    }

    public static PlayerMapState fromId(int id) {
        PlayerMapState state = BY_ID.get(id);
        if (state == null) {
            throw new IllegalStateException("未知玩家地图状态 ID: " + id);
        }
        return state;
    }

    private static Map<Integer, PlayerMapState> createById() {
        Map<Integer, PlayerMapState> result = new HashMap<>();
        for (PlayerMapState state : values()) {
            PlayerMapState old = result.put(state.id, state);
            if (old != null) {
                throw new IllegalStateException("玩家地图状态 ID 重复: " + state.id);
            }
        }
        return Map.copyOf(result);
    }
}
