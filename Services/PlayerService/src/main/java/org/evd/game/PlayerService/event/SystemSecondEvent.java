package org.evd.game.PlayerService.event;

import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;

/**
 * 系统秒事件
 */
public record SystemSecondEvent(
        long playerId,
        int taskId,
        int progress
) implements Event {

    public interface Listener extends EventListener {
        void onEvent(SystemSecondEvent event);
    }
}
