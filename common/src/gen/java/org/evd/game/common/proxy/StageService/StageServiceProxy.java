package org.evd.game.common.proxy.StageService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;

/**
* 根据StageServiceService生成的代理类
*/
public final class StageServiceProxy {

    private static final StageServiceProxy INSTANCE = new StageServiceProxy();

    private StageServiceProxy() {
    }

    public static StageServiceProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_STAGESERVICE_DOSOME1_0 = 0;
        public final static int ENUM_STAGESERVICE_DOSOME2_1 = 1;
        public final static int ENUM_STAGESERVICE_DOSOME3_2 = 2;
    }

    /**
    * 对应源方法: org.evd.game.StageService.StageService#doSome1()
    */
    public String doSome1(CallPoint remote, int a, int b){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_DOSOME1_0, new Object[]{a, b});
    }

    public String doSome1(CallPoint remote, int a, int b, long timeoutMillis){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_DOSOME1_0, new Object[]{a, b}, timeoutMillis);
    }

    /**
    * 对应源方法: org.evd.game.StageService.StageService#doSome2()
    */
    public void doSome2(CallPoint remote, int a, int b){
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.ENUM_STAGESERVICE_DOSOME2_1, new Object[]{a, b});
    }


    /**
    * 对应源方法: org.evd.game.StageService.StageService#doSome3()
    */
    public String doSome3(CallPoint remote, int a){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_DOSOME3_2, new Object[]{a});
    }

    public String doSome3(CallPoint remote, int a, long timeoutMillis){
        Service service = Service.getCurrent();
        return (String)service.callWait(remote, EnumCall.ENUM_STAGESERVICE_DOSOME3_2, new Object[]{a}, timeoutMillis);
    }

}
