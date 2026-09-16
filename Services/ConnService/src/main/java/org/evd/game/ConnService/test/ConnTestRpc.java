package org.evd.game.ConnService.test;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.common.serializeBean.ConnService.test.SConnInfo;
import org.evd.game.annotation.actor.ActorType;
import org.evd.game.runtime.actor.ActorId;

/** RPC generator and serialization test entry points. */
@Actor
@RpcHandler
@SuppressWarnings("unused")
public class ConnTestRpc {
    @Rpc(actorType = ActorType.GATE)
    public void connTest1(ActorId actorId) {

    }


    @Rpc(actorType = ActorType.GATE)
    public boolean connTest2(ActorId actorId, int a, Object b, SConnInfo connInfo) {
        return true;
    }

    @Rpc()
    public void connTest3() {

    }


    @Rpc()
    public boolean connTest4(int a, Object b, SConnInfo connInfo) {
        return true;
    }
}
