package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;

@SerializeClass
public class CallResult extends CallBase {
    public boolean success = true;
    public int methodKey;
    public int errorCode;
    public String errorMessage;
    public Object result;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getMethodKey() {
        return methodKey;
    }

    public void setMethodKey(int methodKey) {
        this.methodKey = methodKey;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "CallResult{" +
                "id=" + id +
                ", to=" + to +
                ", from=" + from +
                ", result=" + result +
                ", errorMessage='" + errorMessage + '\'' +
                ", errorCode=" + errorCode +
                ", methodKey=" + methodKey +
                ", success=" + success +
                '}';
    }
}
