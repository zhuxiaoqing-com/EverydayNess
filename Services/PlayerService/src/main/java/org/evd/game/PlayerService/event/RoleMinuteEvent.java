package org.evd.game.PlayerService.event;

import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;

public record RoleMinuteEvent(
        long playerId
) implements Event {

    public interface Listener extends EventListener {
        void onEvent(RoleMinuteEvent event);
    }
}
