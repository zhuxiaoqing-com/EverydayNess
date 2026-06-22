package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.DispatchType;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.support.SysException;

public final class CallFactory {
    private CallFactory() {
    }

    public static CallBase buildServiceRpc(
            Service current,
            CallPoint to,
            int methodKey,
            Object[] params,
            boolean needResult,
            long id
    ) {
        Call call = new Call();
        call.setFrom(current.copyCallPoint());
        call.setTo(copyCallPoint(to));
        call.setId(id);
        call.setMethodKey(methodKey);
        call.setMethodParam(params);
        call.setNeedResult(needResult);
        return call;
    }

    public static CallBase buildServiceClientCmd(
            Service current,
            CallPoint to,
            ClientSessionRef session,
            int msgId,
            Chunk body
    ) {
        Call call = new Call();
        call.setFrom(current.copyCallPoint());
        call.setTo(copyCallPoint(to));
        call.setDispatchType(DispatchType.CLIENT_CMD);
        call.setMethodKey(msgId);
        call.setMethodParam(new Object[]{session, body});
        call.setNeedResult(false);
        return call;
    }

    public static CallBase buildActorRpc(
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
        message.setFrom(current.copyCallPoint());
        message.setTo(new CallPoint(addr.getCallPoint()));
        message.setActorId(actorId == null ? null : new ActorId(actorId));
        message.setId(id);
        message.setMailBoxEpoch(addr.getMailBoxEpoch());
        message.setMethodKey(methodKey);
        message.setMethodParam(params);
        message.setNeedResult(needResult);
        return message;
    }

    public static CallBase buildActorClientCmd(
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
        message.setFrom(current.copyCallPoint());
        message.setTo(new CallPoint(addr.getCallPoint()));
        message.setActorId(actorId == null ? null : new ActorId(actorId));
        message.setMailBoxEpoch(addr.getMailBoxEpoch());
        message.setDispatchType(DispatchType.CLIENT_CMD);
        message.setMethodKey(msgId);
        message.setMethodParam(new Object[]{session, body});
        message.setNeedResult(false);
        return message;
    }

    private static CallPoint copyCallPoint(CallPoint to) {
        if (to == null) {
            throw new SysException("call point is null");
        }
        return new CallPoint(to);
    }
}
