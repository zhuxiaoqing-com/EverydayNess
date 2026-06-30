package org.evd.game.PlayerService.system;

import org.evd.game.PlayerService.event.RoleMidnightEvent;
import org.evd.game.PlayerService.event.RoleMinuteEvent;
import org.evd.game.annotation.Actor;

/**
 * @author zhuxiaoqing
 * @Description: PlayerLoginActor
 * @Date 2026/6/30 9:50
 **/
@Actor
public class PlayerLoginActor implements RoleMidnightEvent.Listener, RoleMinuteEvent.Listener {
    @Override
    public void onEvent(RoleMidnightEvent event) {

    }

    @Override
    public void onEvent(RoleMinuteEvent event) {

    }
}
