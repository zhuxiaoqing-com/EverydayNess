package org.evd.game.common.serializeBean.SdkService.login;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;

@SerializeClass
public class SdkValidateResult implements ISerializable {
    private boolean success;
    private String message;

    public SdkValidateResult() {
    }

    public SdkValidateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
