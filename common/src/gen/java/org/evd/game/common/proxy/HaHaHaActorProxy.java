package org.evd.game.common.proxy;

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;

/**
* 根据HaHaHaActorService生成的代理类
*/
public class HaHaHaActorProxy extends RPCProxyBase {

    public final static class EnumCall{
        public final static int ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT = 3;
        public final static int ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT = 4;
    }

    private HaHaHaActorProxy(CallPoint callPoint){
        this.remote = callPoint;
    }
    public static HaHaHaActorProxy inst(CallPoint callPoint) {
        return new HaHaHaActorProxy(callPoint);
    }

    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc1()
    */
    public void rpc1(int a, int b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT, new Object[]{a, b});
    }
    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc2()
    */
    public void rpc2(Object a, Object b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT, new Object[]{a, b});
    }
}
