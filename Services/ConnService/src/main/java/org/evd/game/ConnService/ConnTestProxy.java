package org.evd.game.ConnService;

import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.serializeBean.ConnService.test.ConnInfo;
import org.evd.game.annotation.ActorType;

/**
 * @author zhuxiaoqing
 * @Description: ConnTestProxy
 * @Date 2026/5/14 13:55
 **/
@Actor
public class ConnTestProxy {
    @Rpc(actorType = ActorType.GATE)
    public void connTest1() {

    }


    @Rpc(actorType = ActorType.GATE)
    public boolean connTest2(int a, Object b, ConnInfo connInfo) {
        return true;
    }

    @Rpc()
    public void connTest3() {

    }


    @Rpc()
    public boolean connTest4(int a, Object b, ConnInfo connInfo) {
        return true;
    }
}
