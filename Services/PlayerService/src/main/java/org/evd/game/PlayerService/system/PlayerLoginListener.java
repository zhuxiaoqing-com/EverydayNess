package org.evd.game.PlayerService.system;

import org.evd.game.PlayerService.event.RoleMidnightEvent;
import org.evd.game.PlayerService.event.RoleMinuteEvent;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.EventHandler;
import org.evd.game.runtime.annotation.GameEvent;

/**
 * @author zhuxiaoqing
 * @Description: PlayerLoginListener
 * @Date 2026/6/30 9:50
 **/

@GameEvent(
        event = RoleMinuteEvent.class,
        dependsOn = {PlayerBagListener.class}
)
/*@GameEvent(
        event = RoleMinuteEvent.class,
        dependsOn = {PlayerLoginListener.class}
)*/
@Actor
@EventHandler
public class PlayerLoginListener implements RoleMidnightEvent.Listener, RoleMinuteEvent.Listener {
    @Override
    public void onEvent(RoleMidnightEvent event) {

    }

    @Override
    public void onEvent(RoleMinuteEvent event) {

    }
}
