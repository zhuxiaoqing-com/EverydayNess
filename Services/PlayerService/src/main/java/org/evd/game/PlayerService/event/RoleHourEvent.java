package org.evd.game.PlayerService.event;

import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;

/** 玩家小时事件。 */
public record RoleHourEvent(
        long playerId
) implements Event {

    public interface Listener extends EventListener {
        void onEvent(RoleHourEvent event);
    }
}
