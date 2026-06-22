package org.evd.game.common.proxy;

import org.evd.game.runtime.mailbox.MessageLocationSender;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorType;
import org.evd.game.common.serializeBean.ConnInfo;

/**
* 根据ConnTestProxyService生成的代理类
*/
public final class ConnTestProxyProxy {

    private static final ConnTestProxyProxy INSTANCE = new ConnTestProxyProxy();
    private static final MessageLocationSender locationSender = new MessageLocationSender();

    private ConnTestProxyProxy() {
    }

    public static ConnTestProxyProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_CONNTESTPROXY_CONNTEST2_4 = 4;
        public final static int ENUM_CONNTESTPROXY_CONNTEST3_5 = 5;
    }

    /**
    * 对应源方法: org.evd.game.ConnService.ConnTestProxy#connTest2()
    */
    public void connTest2(long actorUniqueId, int a, Object b, ConnInfo connInfo){
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        locationSender.send(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST2_4, new Object[]{a, b, connInfo});
    }


    /**
    * 对应源方法: org.evd.game.ConnService.ConnTestProxy#connTest3()
    */
    public void connTest3(long actorUniqueId){
        ActorId actorId = new ActorId(ActorType.GATE, actorUniqueId);
        locationSender.send(actorId, EnumCall.ENUM_CONNTESTPROXY_CONNTEST3_5, new Object[]{});
    }


}
