package org.evd.game.SdkService;

import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SdkService.login.SdkValidateResult;
import org.evd.game.runtime.Service;

/** SdkService 校验 RPC 入口。 */
@Actor
@RpcHandler
public final class SdkServiceRpc {
    @Rpc
    public SdkValidateResult requestValidate(String userId, String sdkToken) {
        return Service.getCurrent(SdkService.class).requestValidate(userId, sdkToken);
    }
}
