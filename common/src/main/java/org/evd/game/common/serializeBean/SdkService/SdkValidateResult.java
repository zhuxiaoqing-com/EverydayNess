package org.evd.game.common.serializeBean.SdkService;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

@SerializeClass
public class SdkValidateResult implements ISerializable {
    @SerializeField
    private boolean success;
    @SerializeField
    private String message;

    public SdkValidateResult() {
    }

    public SdkValidateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
