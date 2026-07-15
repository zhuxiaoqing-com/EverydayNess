package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;

/** 服务级关服的最终结果；success 代表目标服务的 onClose 已正常结束。 */
@SerializeClass
public class CallServiceStopResult {
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
