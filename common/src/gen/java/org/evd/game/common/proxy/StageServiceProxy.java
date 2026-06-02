package org.evd.game.common.proxy;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据StageServiceService生成的代理类
*/
public final class StageServiceProxy {

    private StageServiceProxy() {
    }

    public final static class EnumCall{
        public final static int ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC1_LONG_INT_INT = 0;
        public final static int ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC2_LONG_OBJECT_OBJECT = 1;
        public final static int ENUM_STAGESERVICE_STRING_DOSOME1_INT_INT = 2;
        public final static int ENUM_STAGESERVICE_VOID_DOSOME2_INT_INT = 3;
        public final static int ENUM_STAGESERVICE_STRING_DOSOME3_INT = 4;
    }

    /**
    * @see org.evd.game.StageService.StageService#callHaHaHaActorRpc1()
    */
    public static void callHaHaHaActorRpc1(CallPoint remote, long actorId, int a, int b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC1_LONG_INT_INT, new Object[]{actorId, a, b});
    }
    /**
    * @see org.evd.game.StageService.StageService#callHaHaHaActorRpc2()
    */
    public static void callHaHaHaActorRpc2(CallPoint remote, long actorId, Object a, Object b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICE_VOID_CALLHAHAHAACTORRPC2_LONG_OBJECT_OBJECT, new Object[]{actorId, a, b});
    }
    /**
    * @see org.evd.game.StageService.StageService#doSome1()
    */
    public static String doSome1(CallPoint remote, int a, int b){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_STRING_DOSOME1_INT_INT, new Object[]{a, b});
    }
    public static String doSome1(CallPoint remote, int a, int b, long timeoutMillis){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_STRING_DOSOME1_INT_INT, new Object[]{a, b}, timeoutMillis);
    }
    /**
    * @see org.evd.game.StageService.StageService#doSome2()
    */
    public static void doSome2(CallPoint remote, int a, int b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICE_VOID_DOSOME2_INT_INT, new Object[]{a, b});
    }
    /**
    * @see org.evd.game.StageService.StageService#doSome3()
    */
    public static String doSome3(CallPoint remote, int a){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_STRING_DOSOME3_INT, new Object[]{a});
    }
    public static String doSome3(CallPoint remote, int a, long timeoutMillis){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_STRING_DOSOME3_INT, new Object[]{a}, timeoutMillis);
    }
}
