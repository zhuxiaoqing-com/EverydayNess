package org.evd.game.runtime;

import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.SysException;

final class ActorForwarding {
    static final int MESSAGE_METHOD = Integer.MIN_VALUE + 1;
    static final int REQUEST_METHOD = Integer.MIN_VALUE + 2;

    private ActorForwarding() {
    }

    static Call unwrapOrOriginal(String serviceId, Call call) {
        if (!isForwardMethod(call.methodKey)) {
            return call;
        }

        Object[] envelopeParams = call.methodParam;
        if (call.actorId == null) {
            throw RpcCallException.actorNotFound((ActorId) null);
        }
        if (envelopeParams == null || envelopeParams.length != 2) {
            throw new SysException("actor forward payload invalid: service={}, methodKey={}", serviceId, call.methodKey);
        }
        if (!(envelopeParams[0] instanceof Integer methodKey)) {
            throw new SysException("actor forward methodKey invalid: service={}, methodKey={}", serviceId, call.methodKey);
        }
        if (!(envelopeParams[1] instanceof Object[] methodParams)) {
            throw new SysException("actor forward params invalid: service={}, methodKey={}", serviceId, call.methodKey);
        }

        Call innerCall = new Call();
        innerCall.from = call.from;
        innerCall.to = call.to;
        innerCall.id = call.id;
        innerCall.actorId = new ActorId(call.actorId);
        innerCall.methodKey = methodKey;
        innerCall.methodParam = methodParams;
        innerCall.needResult = call.needResult;
        return innerCall;
    }

    static Call createMessageEnvelope(CallPoint from, CallPoint to, ActorId actorId, int methodKey, Object[] params) {
        return createEnvelope(from, to, actorId, MESSAGE_METHOD, methodKey, params, false);
    }

    static Call createRequestEnvelope(CallPoint from, CallPoint to, ActorId actorId, int methodKey, Object[] params) {
        return createEnvelope(from, to, actorId, REQUEST_METHOD, methodKey, params, true);
    }

    private static boolean isForwardMethod(int methodKey) {
        return methodKey == MESSAGE_METHOD || methodKey == REQUEST_METHOD;
    }

    private static Call createEnvelope(
            CallPoint from,
            CallPoint to,
            ActorId actorId,
            int forwardMethodKey,
            int methodKey,
            Object[] params,
            boolean needResult
    ) {
        Call call = new Call();
        call.from = from;
        call.to = to;
        call.actorId = actorId == null ? null : new ActorId(actorId);
        call.methodKey = forwardMethodKey;
        call.methodParam = new Object[]{methodKey, params};
        call.needResult = needResult;
        return call;
    }
}
