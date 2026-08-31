package org.evd.game.PlayerService.event;

import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;

/** 系统小时事件。 */
public record SystemHourEvent() implements Event {
    public static final SystemHourEvent INSTANCE = new SystemHourEvent();

    public interface Listener extends EventListener {
        void onEvent(SystemHourEvent event);
    }
}
