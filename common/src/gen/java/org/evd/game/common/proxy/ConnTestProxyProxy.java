package org.evd.game.common.proxy;

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;
        import org.evd.game.common.serializeBean.ConnInfo;

/**
* 根据ConnTestProxyService生成的代理类
*/
public class ConnTestProxyProxy extends RPCProxyBase {

    public final static class EnumCall{
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST1 = 5;
        public final static int ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_COMMON_SERIALIZEBEAN_CONNINFO = 6;
    }

    private ConnTestProxyProxy(CallPoint callPoint){
        this.remote = callPoint;
    }
    public static ConnTestProxyProxy inst(CallPoint callPoint) {
        return new ConnTestProxyProxy(callPoint);
    }

    /**
    * @see org.evd.game.ConnService.ConnTestProxy#connTest1()
    */
    public void connTest1(){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST1, new Object[]{});
    }
    /**
    * @see org.evd.game.ConnService.ConnTestProxy#connTest2()
    */
    public void connTest2(int a, Object b, org.evd.game.common.serializeBean.ConnInfo connInfo){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_CONNTESTPROXY_VOID_CONNTEST2_INT_OBJECT_ORG_EVD_GAME_COMMON_SERIALIZEBEAN_CONNINFO, new Object[]{a, b, connInfo});
    }
}
