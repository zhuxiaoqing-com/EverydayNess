package org.evd.game.runtime.rpcProxyInterface;

import org.evd.game.runtime.call.*;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RpcErrorCodes;
import org.evd.game.runtime.support.exception.SysException;

public final class RpcInboundDispatcher {
    private final Service service;

    public RpcInboundDispatcher(Service service) {
        this.service = service;
    }

    public void handle(CallBase callBase) {
        ContinuationRuntime continuationRuntime = service.continuationRuntime();
        if (callBase instanceof ActorMessage actorMessage) {
            service.getProcessInnerSender().dispatch(actorMessage);
            return;
        } else if (callBase instanceof Call call) {
            service.continuationRuntime().createAndEnterQueue(() -> dispatch(call), null, Task.Reason.RPC, new ContinuationDebugInfo.RpcDebugInfo(call));
            return;
        } else if(callBase instanceof CallResult callResult){
            boolean completed = service.handleRpcResult(callResult);
            if (!completed) {
                LogCore.core.warn("callback is null or timeout, waitId={}, methodKey={}, success={}, errorCode={}, message={}",
                        callResult.id, callResult.methodKey, callResult.success, callResult.errorCode, callResult.errorMessage);
            }
            return;
        } else if (callBase instanceof RpcCallBase rpcCallBase){
            service.continuationRuntime().createAndEnterQueue(() -> dispatchBusinessCall(rpcCallBase), null, Task.Reason.RPC, new ContinuationDebugInfo.RpcDebugInfo(rpcCallBase));
            return;
        }
    }

    void dispatch(Call call) {
        dispatchBusinessCall(call);
    }

    public void dispatchMailBoxMessage(ActorMessage message) {
        dispatchBusinessCall(message);
    }

    public void dispatchBusinessCall(RpcCallBase call) {
        if (call.dispatchType == DispatchType.CLIENT_CMD) {
            dispatchClientCmdCall(call);
            return;
        }

        Object[] args = call.methodParam;
        if (call.needResult) {
            CallResult callReturn = call.createReturn();
            try {
                if(call.dispatchType == DispatchType.STOP_SERVICE) {
                    callReturn.result = service.rpcStop();
                } else {
                    callReturn.result = service.getRpcMethodInvoker().invokeBusiness(call.methodKey, args);
                }
            } catch (Throwable e) {
                rethrowFatal(e);
                LogCore.core.error("rpc return dispatch failed: service={}, methodKey={}",
                        service.getId(), call.methodKey, e);
                fillRpcFailure(callReturn, e);
            }
            service.getRpcOutboundGateway().send(callReturn);
            return;
        }

        try {
            service.getRpcMethodInvoker().invokeBusiness(call.methodKey, args);
        } catch (Exception e) {
            LogCore.core.error("rpc dispatch failed: service={}, methodKey={}", service.getId(), call.methodKey, e);
        }
    }

    private void dispatchClientCmdCall(RpcCallBase call) {
        if (call.needResult) {
            CallResult callReturn = call.createReturn();
            try {
                service.getRpcMethodInvoker().dispatchClientCmd(call.methodKey, call.methodParam);
            } catch (Throwable e) {
                rethrowFatal(e);
                LogCore.core.error("client cmd return dispatch failed: service={}, msgId={}",
                        service.getId(), call.methodKey, e);
                fillRpcFailure(callReturn, e);
            }
            service.getRpcOutboundGateway().send(callReturn);
            return;
        }

        try {
            service.getRpcMethodInvoker().dispatchClientCmd(call.methodKey, call.methodParam);
        } catch (Exception e) {
            LogCore.core.error("client cmd dispatch failed: service={}, msgId={}",
                    service.getId(), call.methodKey, e);
        }
    }

    private void fillRpcFailure(CallResult callReturn, Throwable e) {
        callReturn.success = false;
        if (e instanceof SysException sysException) {
            callReturn.errorCode = sysException.getErrorCode();
            callReturn.errorMessage = sysException.getMessage();
            return;
        }
        callReturn.errorCode = RpcErrorCodes.UNKNOWN;
        callReturn.errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static void rethrowFatal(Throwable throwable) {
        if (throwable instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
    }
}
