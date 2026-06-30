package org.evd.game.PlayerService.event;

import org.evd.game.runtime.event.Event;
import org.evd.game.runtime.event.EventListener;

/**
 * 玩家午夜事件
 */
public record RoleMidnightEvent(
        long playerId,
        int taskId,
        int progress
) implements Event {

    public interface Listener extends EventListener {
        void onEvent(RoleMidnightEvent event);
    }
}
