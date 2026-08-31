package org.evd.game.PlayerService.event;

import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;

/** 系统半小时事件。 */
public record SystemHalfHourEvent() implements Event {
    public static final SystemHalfHourEvent INSTANCE = new SystemHalfHourEvent();

    public interface Listener extends EventListener {
        void onEvent(SystemHalfHourEvent event);
    }
}
