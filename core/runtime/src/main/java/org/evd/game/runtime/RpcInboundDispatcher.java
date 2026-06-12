package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorRegistry;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallResult;
import org.evd.game.runtime.call.DispatchType;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.mailbox.MailBoxComponent;
import org.evd.game.runtime.mailbox.ProcessInnerSender;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;
import org.evd.game.runtime.support.SysException;

final class RpcInboundDispatcher {
    private final Service service;
    private final ProcessInnerSender processInnerSender;
    private final RpcMethodInvoker rpcMethodInvoker;
    private final RpcOutboundGateway rpcOutboundGateway;

    RpcInboundDispatcher(
            Service service,
            ProcessInnerSender processInnerSender,
            RpcMethodInvoker rpcMethodInvoker,
            RpcOutboundGateway rpcOutboundGateway
    ) {
        this.service = service;
        this.processInnerSender = processInnerSender;
        this.rpcMethodInvoker = rpcMethodInvoker;
        this.rpcOutboundGateway = rpcOutboundGateway;
    }

    void handle(CallBase callBase) {
        Task.ContinuationWrapper context;
        if (callBase instanceof Call call) {
            context = service.createRpcContinuation(() -> dispatch(call), call.getActorId(), call.methodKey);
        } else if (callBase instanceof ActorMessage actorMessage) {
            processInnerSender.dispatch(actorMessage);
            return;
        } else {
            CallResult callResult = (CallResult) callBase;
            context = service.takeWaitContinuation(callResult.id);
            if (context == null) {
                LogCore.core.warn("callback is null or timeout, waitId={}", callResult.id);
                return;
            }
            if (callResult.success) {
                context.setResult(callResult.result);
            } else {
                context.setFailure(new RpcCallException(
                        callResult.errorCode,
                        "rpc call failed: service=" + service.getId() + ", waitId=" + callResult.id
                                + ", errorCode=" + callResult.errorCode + ", message=" + callResult.errorMessage));
            }
        }
        service.queueContinuation(context);
    }

    void dispatch(Call call) {
        if (call.actorId != null) {
            dispatchActorCallWithResult(call);
            return;
        }
        dispatchBusinessCall(call);
    }

    void dispatchMailBoxMessage(ActorMessage message) {
        Call call = new Call();
        call.from = service.copyCallPoint(message.getFrom());
        call.to = service.copyCallPoint(message.getTo());
        call.id = message.getId();
        call.actorId = message.getActorId() == null ? null : new ActorId(message.getActorId());
        call.dispatchType = message.getDispatchType();
        call.methodKey = message.getMethodKey();
        call.methodParam = message.getMethodParam();
        call.needResult = message.isNeedResult();
        dispatchBusinessCall(call);
    }

    private void dispatchActorCallWithResult(Call call) {
        if (!call.needResult) {
            dispatchActorCall(call);
            return;
        }

        CallResult callReturn = call.createReturn();
        try {
            dispatchActorCall(call);
        } catch (Throwable e) {
            LogCore.core.error("actor rpc dispatch failed: service={}, actorId={}, methodKey={}",
                    service.getId(), call.actorId, call.methodKey, e);
            fillRpcFailure(callReturn, e);
            rpcOutboundGateway.send(callReturn);
        }
    }

    private void dispatchActorCall(Call call) {
        ActorRegistry.Registration registration = service.requireActorRegistration(call.actorId);
        processInnerSender.dispatch(toActorMessage(call, registration));
    }

    private void dispatchBusinessCall(Call call) {
        if (call.dispatchType == DispatchType.CLIENT_CMD) {
            dispatchClientCmdCall(call);
            return;
        }

        Object[] args = call.methodParam;
        if (call.needResult) {
            CallResult callReturn = call.createReturn();
            try {
                callReturn.result = rpcMethodInvoker.invokeBusiness(call.methodKey, args);
            } catch (Throwable e) {
                LogCore.core.error("rpc return dispatch failed: service={}, methodKey={}",
                        service.getId(), call.methodKey, e);
                fillRpcFailure(callReturn, e);
            }
            rpcOutboundGateway.send(callReturn);
            return;
        }

        try {
            rpcMethodInvoker.invokeBusiness(call.methodKey, args);
        } catch (Exception e) {
            LogCore.core.error("rpc dispatch failed: service={}, methodKey={}", service.getId(), call.methodKey, e);
        }
    }

    private void dispatchClientCmdCall(Call call) {
        if (call.needResult) {
            CallResult callReturn = call.createReturn();
            try {
                rpcMethodInvoker.dispatchClientCmd(call.methodKey, call.methodParam);
            } catch (Throwable e) {
                LogCore.core.error("client cmd return dispatch failed: service={}, msgId={}",
                        service.getId(), call.methodKey, e);
                fillRpcFailure(callReturn, e);
            }
            rpcOutboundGateway.send(callReturn);
            return;
        }

        try {
            rpcMethodInvoker.dispatchClientCmd(call.methodKey, call.methodParam);
        } catch (Exception e) {
            LogCore.core.error("client cmd dispatch failed: service={}, msgId={}",
                    service.getId(), call.methodKey, e);
        }
    }

    private ActorMessage toActorMessage(Call call, ActorRegistry.Registration registration) {
        MailBoxComponent mailBoxComponent = registration.getMailBoxComponent();
        ActorMessage actorMessage = new ActorMessage();
        actorMessage.setFrom(service.copyCallPoint(call.from));
        actorMessage.setTo(service.copyCallPoint(call.to));
        actorMessage.setId(call.id);
        actorMessage.setActorId(call.actorId == null ? null : new ActorId(call.actorId));
        actorMessage.setOwnerInstanceId(mailBoxComponent.getOwnerInstanceId());
        actorMessage.setMailBoxInstanceId(mailBoxComponent.getInstanceId());
        actorMessage.setDispatchType(call.dispatchType);
        actorMessage.setMethodKey(call.methodKey);
        actorMessage.setMethodParam(call.methodParam);
        actorMessage.setNeedResult(call.needResult);
        return actorMessage;
    }

    private void fillRpcFailure(CallResult callReturn, Throwable e) {
        callReturn.success = false;
        if (e instanceof RpcCallException rpcCallException) {
            callReturn.errorCode = rpcCallException.getErrorCode();
            callReturn.errorMessage = rpcCallException.getMessage();
            return;
        }
        if (e instanceof SysException sysException) {
            callReturn.errorCode = RpcErrorCodes.UNKNOWN;
            callReturn.errorMessage = sysException.getMessage();
            return;
        }
        callReturn.errorCode = RpcErrorCodes.UNKNOWN;
        callReturn.errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
