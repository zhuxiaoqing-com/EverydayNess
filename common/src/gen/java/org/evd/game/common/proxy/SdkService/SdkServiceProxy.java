package org.evd.game.common.proxy.SdkService;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.serializeBean.SdkService.login.SdkValidateResult;

/**
* 根据SdkServiceService生成的代理类
*/
public final class SdkServiceProxy {

    private static final SdkServiceProxy INSTANCE = new SdkServiceProxy();

    private SdkServiceProxy() {
    }

    public static SdkServiceProxy inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
        public final static int ENUM_SDKSERVICE_REQUESTVALIDATE_0 = 0;
    }

    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<SdkValidateResult> callRequestValidate(CallPoint remote, String userId, String sdkToken){
        return RpcResult.call(() -> inst().requestValidate(remote, userId, sdkToken));
    }



    /**
    * 对应源方法: org.evd.game.SdkService.SdkService#requestValidate()
    */
    public SdkValidateResult requestValidate(CallPoint remote, String userId, String sdkToken){
        Service service = Service.getCurrent();
        return (SdkValidateResult)service.callWait(remote, EnumCall.ENUM_SDKSERVICE_REQUESTVALIDATE_0, new Object[]{userId, sdkToken});
    }


}
