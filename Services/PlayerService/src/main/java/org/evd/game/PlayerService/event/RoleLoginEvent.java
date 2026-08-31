package org.evd.game.PlayerService.event;

import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;

/**
 * 玩家上线事件
 */
public record RoleLoginEvent(
        long playerId
) implements Event {

    public interface Listener extends EventListener {
        void onEvent(RoleLoginEvent event);
    }
}
