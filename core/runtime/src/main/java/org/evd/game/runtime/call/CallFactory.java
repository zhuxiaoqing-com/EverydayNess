package org.evd.game.runtime.call;

import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.support.exception.SysException;

public final class CallFactory {
    private CallFactory() {
    }

    public static Call buildServiceRpc(
            Service current,
            CallPoint to,
            int methodKey,
            Object[] params,
            boolean needResult,
            long id
    ) {
        Call call = new Call();
        call.setFrom(current.getCallPoint());
        call.setTo(to);
        call.setId(id);
        call.setMethodKey(methodKey);
        call.setMethodParam(params);
        call.setNeedResult(needResult);
        return call;
    }

    public static Call buildServiceClientCmd(
            Service current,
            CallPoint to,
            ClientSessionRef session,
            int msgId,
            Chunk body
    ) {
        Call call = new Call();
        call.setFrom(current.getCallPoint());
        call.setTo(to);
        call.setDispatchType(DispatchType.CLIENT_CMD);
        call.setMethodKey(msgId);
        call.setMethodParam(new Object[]{session, body});
        call.setNeedResult(false);
        return call;
    }

    public static ActorMessage buildActorRpc(
            Service current,
            ActorAddress addr,
            ActorId actorId,
            int methodKey,
            Object[] params,
            boolean needResult,
            long id
    ) {
        if (addr == null || addr.getCallPoint() == null) {
            throw new SysException("actor address is null: actorId={}", actorId);
        }

        ActorMessage message = new ActorMessage();
        message.setFrom(current.getCallPoint());
        message.setTo(new CallPoint(addr.getCallPoint()));
        message.setActorId(actorId == null ? null : new ActorId(actorId));
        message.setId(id);
        message.setMailBoxEpoch(addr.getMailBoxEpoch());
        message.setMethodKey(methodKey);
        message.setMethodParam(params);
        message.setNeedResult(needResult);
        return message;
    }

    public static ActorMessage buildActorClientCmd(
            Service current,
            ActorAddress addr,
            ActorId actorId,
            int msgId,
            ClientSessionRef session,
            Chunk body
    ) {
        if (addr == null || addr.getCallPoint() == null) {
            throw new SysException("actor address is null: actorId={}", actorId);
        }

        ActorMessage message = new ActorMessage();
        message.setFrom(current.getCallPoint());
        message.setTo(new CallPoint(addr.getCallPoint()));
        message.setActorId(actorId == null ? null : new ActorId(actorId));
        message.setMailBoxEpoch(addr.getMailBoxEpoch());
        message.setDispatchType(DispatchType.CLIENT_CMD);
        message.setMethodKey(msgId);
        message.setMethodParam(new Object[]{session, body});
        message.setNeedResult(false);
        return message;
    }


    public static CallServiceStop buildCallServiceStop(Service current, CallPoint to) {
        CallServiceStop callServiceStop = new CallServiceStop();
        callServiceStop.setFrom(current.getCallPoint());
        callServiceStop.setTo(to);
        callServiceStop.setDispatchType(DispatchType.STOP_SERVICE);
        callServiceStop.setNeedResult(true);
        return callServiceStop;
    }

}
