package org.evd.game.SdkService;

import org.evd.game.annotation.Rpc;
import org.evd.game.common.serializeBean.SdkService.SdkValidateResult;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.support.LogCore;

public class SdkService extends Service {
    private static final boolean LOCAL_STUB_ENABLED =
            Boolean.parseBoolean(System.getProperty("sdk.stub.enabled", "true"));

    public SdkService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Rpc
    public SdkValidateResult requestValidate(String userId, String sdkToken) {
        boolean success = LOCAL_STUB_ENABLED && userId != null && !userId.isBlank()
                && sdkToken != null && !sdkToken.isBlank();
        String message;
        if (!LOCAL_STUB_ENABLED) {
            message = "sdk stub disabled";
        } else if (success) {
            message = "ok";
        } else {
            message = userId == null || userId.isBlank() ? "userId 不能为空" : "sdkToken 不能为空";
        }

        LogCore.core.info("SdkService 校验请求: service={}, userId={}, success={}",
                id, userId, success);
        return new SdkValidateResult(success, message);
    }
}
