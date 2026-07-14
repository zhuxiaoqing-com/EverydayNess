package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.runtime.actor.ActorId;

import java.util.Arrays;

@SerializeClass
public class ActorMessage extends CallBase {
    private ActorId actorId;
    private long mailBoxEpoch;
    private int dispatchType = DispatchType.RPC;
    private int methodKey;
    private Object[] methodParam;
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

    @Override
    public String toString() {
        return "ActorMessage{" +
                "id=" + id +
                ", to=" + to +
                ", from=" + from +
                ", needResult=" + needResult +
                ", methodParam=" + Arrays.toString(methodParam) +
                ", methodKey=" + methodKey +
                ", dispatchType=" + dispatchType +
                ", mailBoxEpoch=" + mailBoxEpoch +
                ", actorId=" + actorId +
                '}';
    }
}
