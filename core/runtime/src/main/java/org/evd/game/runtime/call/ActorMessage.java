package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.runtime.actor.ActorId;

import java.util.Arrays;

@SerializeClass
public class ActorMessage extends RpcCallBase {
    private ActorId actorId;
    private long mailBoxEpoch;

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
