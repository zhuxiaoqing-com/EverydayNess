package org.evd.game.PlayerService.event;

import org.evd.game.runtime.event.Event;
import org.evd.game.runtime.event.EventListener;

public record RoleMinuteEvent(
        long playerId,
        int taskId,
        int progress
) implements Event {

    public interface Listener extends EventListener {
        void onEvent(RoleMinuteEvent event);
    }
}
