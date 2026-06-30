package org.evd.game.PlayerService.event;

import org.evd.game.runtime.event.Event;
import org.evd.game.runtime.event.EventListener;

/**
 * 系统分钟事件
 */
public record SystemMinuteEvent(
        long playerId,
        int taskId,
        int progress
) implements Event {

    public interface Listener extends EventListener {
        void onEvent(SystemMinuteEvent event);
    }
}
