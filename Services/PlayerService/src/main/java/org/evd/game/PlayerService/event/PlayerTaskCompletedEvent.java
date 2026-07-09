package org.evd.game.PlayerService.event;

import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;

    public record PlayerTaskCompletedEvent(
            long playerId,
            int taskId,
            int progress
    ) implements Event {

    public interface Listener extends EventListener {
        void onEvent(PlayerTaskCompletedEvent event);
    }
}
