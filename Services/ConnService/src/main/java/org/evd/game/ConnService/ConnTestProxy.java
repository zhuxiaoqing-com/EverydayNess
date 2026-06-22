package org.evd.game.ConnService;

import org.evd.game.annotation.Rpc;
import org.evd.game.common.serializeBean.ConnInfo;
import org.evd.game.runtime.actor.ActorType;

/**
 * @author zhuxiaoqing
 * @Description: ConnTestProxy
 * @Date 2026/5/14 13:55
 **/
public class ConnTestProxy {
    @Rpc(actorType = ActorType.GATE)
    public void connTest3() {

    }


    @Rpc(actorType = ActorType.GATE)
    public void connTest2(int a, Object b, ConnInfo connInfo) {

    }

}
