package org.evd.game.common.proxy;

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;
        import org.evd.game.runtime.ClientSessionRef;
        import org.evd.game.runtime.Chunk;

/**
* 根据StageServiceService生成的代理类
*/
public class StageServiceProxy extends RPCProxyBase {

    public final static class EnumCall{
        public final static int ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC1_LONG_INT_INT = 0;
        public final static int ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC2_LONG_OBJECT_OBJECT = 1;
        public final static int ENUM_STAGESERVICE_STRING_DOSOME1_INT_INT = 2;
        public final static int ENUM_STAGESERVICE_VOID_DOSOME2_INT_INT = 3;
        public final static int ENUM_STAGESERVICE_STRING_DOSOME3_INT = 4;
        public final static int ENUM_STAGESERVICE_VOID_FORWARDCLIENTCMD_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK = 5;
    }

    private StageServiceProxy(CallPoint callPoint){
        this.remote = callPoint;
    }
    public static StageServiceProxy inst(CallPoint callPoint) {
        return new StageServiceProxy(callPoint);
    }

    /**
    * @see org.evd.game.StageService.StageService#callHaHaHaActorRpc1()
    */
    public void callHaHaHaActorRpc1(long actorId, int a, int b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC1_LONG_INT_INT, new Object[]{actorId, a, b});
    }
    /**
    * @see org.evd.game.StageService.StageService#callHaHaHaActorRpc2()
    */
    public void callHaHaHaActorRpc2(long actorId, Object a, Object b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC2_LONG_OBJECT_OBJECT, new Object[]{actorId, a, b});
    }
    /**
    * @see org.evd.game.StageService.StageService#doSome1()
    */
    public String doSome1(int a, int b){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_STRING_DOSOME1_INT_INT, new Object[]{a, b});
    }
    public String doSome1(int a, int b, long timeoutMillis){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_STRING_DOSOME1_INT_INT, new Object[]{a, b}, timeoutMillis);
    }
    /**
    * @see org.evd.game.StageService.StageService#doSome2()
    */
    public void doSome2(int a, int b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICE_VOID_DOSOME2_INT_INT, new Object[]{a, b});
    }
    /**
    * @see org.evd.game.StageService.StageService#doSome3()
    */
    public String doSome3(int a){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_STRING_DOSOME3_INT, new Object[]{a});
    }
    public String doSome3(int a, long timeoutMillis){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_STRING_DOSOME3_INT, new Object[]{a}, timeoutMillis);
    }
    /**
    * @see org.evd.game.StageService.StageService#forwardClientCmd()
    */
    public void forwardClientCmd(org.evd.game.runtime.ClientSessionRef session, int msgId, org.evd.game.runtime.Chunk body){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICE_VOID_FORWARDCLIENTCMD_ORG_EVD_GAME_RUNTIME_CLIENTSESSIONREF_INT_ORG_EVD_GAME_RUNTIME_CHUNK, new Object[]{session, msgId, body});
    }
}
