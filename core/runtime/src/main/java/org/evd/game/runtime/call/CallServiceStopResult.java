package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;

/** 服务级关服结果；success 代表 onStop（包括 MDB 落库）已完成，并已进入最终关闭阶段。 */
@SerializeClass
public class CallServiceStopResult implements ISerializable {
    private boolean success;
    private String errorMessage;

    public CallServiceStopResult() {
    }

    public CallServiceStopResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
