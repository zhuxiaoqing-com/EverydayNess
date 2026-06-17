package org.evd.game.runtime;

import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorRegistry;
import org.evd.game.runtime.call.ActorMessage;
import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallResult;
import org.evd.game.runtime.call.DispatchType;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.mailbox.MailBoxComponent;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;
import org.evd.game.runtime.support.SysException;

final class RpcInboundDispatcher {
    private final Service service;

    RpcInboundDispatcher(Service service) {
        this.service = service;
    }

    void handle(CallBase callBase) {
        ContinuationRuntime continuationRuntime = service.continuationRuntimeInternal();
        Task.ContinuationWrapper context;
        if (callBase instanceof Call call) {
            context = createRpcContinuation(() -> dispatch(call), null, call.methodKey);
        } else if (callBase instanceof ActorMessage actorMessage) {
            service.getProcessInnerSender().dispatch(actorMessage);
            return;
        } else {
            CallResult callResult = (CallResult) callBase;
            context = continuationRuntime.takeWaitContinuation(callResult.id);
            if (context == null) {
                LogCore.core.warn("callback is null or timeout, waitId={}, methodKey={}, success={}, errorCode={}, message={}",
                        callResult.id, callResult.methodKey, callResult.success, callResult.errorCode, callResult.errorMessage);
                return;
            }
            if (callResult.success) {
                context.setResult(callResult.result);
            } else {
                context.setFailure(new RpcCallException(
                        callResult.errorCode,
                        "rpc call failed: service=" + service.id + ", waitId=" + callResult.id
                                + ", methodKey=" + callResult.methodKey
                                + ", errorCode=" + callResult.errorCode + ", message=" + callResult.errorMessage));
            }
        }
        continuationRuntime.queue(context, "rpc");
    }

    void dispatch(Call call) {
        dispatchBusinessCall(call);
    }

    void dispatchMailBoxMessage(ActorMessage message) {
        Call call = new Call();
        call.from = new CallPoint(message.getFrom());
        call.to = new CallPoint(message.getTo());
        call.id = message.getId();
        call.dispatchType = message.getDispatchType();
        call.methodKey = message.getMethodKey();
        call.methodParam = message.getMethodParam();
        call.needResult = message.isNeedResult();
        dispatchBusinessCall(call);
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
                callReturn.result = service.getRpcMethodInvoker().invokeBusiness(call.methodKey, args);
            } catch (Throwable e) {
                LogCore.core.error("rpc return dispatch failed: service={}, methodKey={}",
                        service.id, call.methodKey, e);
                fillRpcFailure(callReturn, e);
            }
            service.getRpcOutboundGateway().send(callReturn);
            return;
        }

        try {
            service.getRpcMethodInvoker().invokeBusiness(call.methodKey, args);
        } catch (Exception e) {
            LogCore.core.error("rpc dispatch failed: service={}, methodKey={}", service.id, call.methodKey, e);
        }
    }

    private void dispatchClientCmdCall(Call call) {
        if (call.needResult) {
            CallResult callReturn = call.createReturn();
            try {
                service.getRpcMethodInvoker().dispatchClientCmd(call.methodKey, call.methodParam);
            } catch (Throwable e) {
                LogCore.core.error("client cmd return dispatch failed: service={}, msgId={}",
                        service.id, call.methodKey, e);
                fillRpcFailure(callReturn, e);
            }
            service.getRpcOutboundGateway().send(callReturn);
            return;
        }

        try {
            service.getRpcMethodInvoker().dispatchClientCmd(call.methodKey, call.methodParam);
        } catch (Exception e) {
            LogCore.core.error("client cmd dispatch failed: service={}, msgId={}",
                    service.id, call.methodKey, e);
        }
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

    private Task.ContinuationWrapper createRpcContinuation(Runnable task, ActorId actorId, int methodKey) {
        ContinuationRuntime continuationRuntime = service.continuationRuntimeInternal();
        Task.ContinuationWrapper continuation = continuationRuntime.create(task, actorId);
        continuation.bindDebugInfo(new Task.RpcDebugInfo(methodKey));
        return continuation;
    }
}
