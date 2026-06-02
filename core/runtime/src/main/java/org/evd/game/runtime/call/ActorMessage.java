package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;

@SerializeClass
public class ActorMessage extends CallBase {
    @SerializeField
    private long ownerInstanceId;
    @SerializeField
    private long mailBoxInstanceId;
    @SerializeField
    private int dispatchType = DispatchType.RPC;
    @SerializeField
    private int methodKey;
    @SerializeField
    private Object[] methodParam;
    @SerializeField
    private boolean needResult;

    public CallResult createReturn() {
        CallResult callResult = new CallResult();
        callResult.from = new CallPoint(this.to);
        callResult.to = new CallPoint(this.from);
        callResult.id = this.id;
        return callResult;
    }

    public long getOwnerInstanceId() {
        return ownerInstanceId;
    }

    public void setOwnerInstanceId(long ownerInstanceId) {
        this.ownerInstanceId = ownerInstanceId;
    }

    public long getMailBoxInstanceId() {
        return mailBoxInstanceId;
    }

    public void setMailBoxInstanceId(long mailBoxInstanceId) {
        this.mailBoxInstanceId = mailBoxInstanceId;
    }

    public int getMethodKey() {
        return methodKey;
    }

    public int getDispatchType() {
        return dispatchType;
    }

    public void setDispatchType(int dispatchType) {
        this.dispatchType = dispatchType;
    }

    public void setMethodKey(int methodKey) {
        this.methodKey = methodKey;
    }

    public Object[] getMethodParam() {
        return methodParam;
    }

    public void setMethodParam(Object[] methodParam) {
        this.methodParam = methodParam;
    }

    public boolean isNeedResult() {
        return needResult;
    }

    public void setNeedResult(boolean needResult) {
        this.needResult = needResult;
    }
}
