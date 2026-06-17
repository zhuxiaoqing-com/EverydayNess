package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.runtime.actor.ActorId;

@SerializeClass
public class ActorMessage extends CallBase {
    @SerializeField
    private ActorId actorId;
    @SerializeField
    private long mailBoxEpoch;
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
        callResult.methodKey = this.methodKey;
        return callResult;
    }

    public ActorId getActorId() {
        return actorId;
    }

    public void setActorId(ActorId actorId) {
        this.actorId = actorId;
    }

    public long getMailBoxEpoch() {
        return mailBoxEpoch;
    }

    public void setMailBoxEpoch(long mailBoxEpoch) {
        this.mailBoxEpoch = mailBoxEpoch;
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
