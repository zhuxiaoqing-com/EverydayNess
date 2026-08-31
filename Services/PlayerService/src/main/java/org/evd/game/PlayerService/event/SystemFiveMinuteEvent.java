package org.evd.game.PlayerService.event;

import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;

/** 系统五分钟事件。 */
public record SystemFiveMinuteEvent() implements Event {
    public static final SystemFiveMinuteEvent INSTANCE = new SystemFiveMinuteEvent();

    public interface Listener extends EventListener {
        void onEvent(SystemFiveMinuteEvent event);
    }
}
