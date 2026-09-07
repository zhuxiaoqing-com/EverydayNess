package org.evd.game.runtime.rpcProxyInterface;

import org.evd.game.runtime.client.ClientCmdRegistryBase;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.support.function.Function0;
import org.evd.game.runtime.support.function.Function1;
import org.evd.game.runtime.support.function.Function10;
import org.evd.game.runtime.support.function.Function2;
import org.evd.game.runtime.support.function.Function3;
import org.evd.game.runtime.support.function.Function4;
import org.evd.game.runtime.support.function.Function5;
import org.evd.game.runtime.support.function.Function6;
import org.evd.game.runtime.support.function.Function7;
import org.evd.game.runtime.support.function.Function8;
import org.evd.game.runtime.support.function.Function9;
import org.evd.game.runtime.support.function.ReturnFunction0;
import org.evd.game.runtime.support.function.ReturnFunction1;
import org.evd.game.runtime.support.function.ReturnFunction10;
import org.evd.game.runtime.support.function.ReturnFunction2;
import org.evd.game.runtime.support.function.ReturnFunction3;
import org.evd.game.runtime.support.function.ReturnFunction4;
import org.evd.game.runtime.support.function.ReturnFunction5;
import org.evd.game.runtime.support.function.ReturnFunction6;
import org.evd.game.runtime.support.function.ReturnFunction7;
import org.evd.game.runtime.support.function.ReturnFunction8;
import org.evd.game.runtime.support.function.ReturnFunction9;
import org.evd.game.runtime.support.function.ActorFunction1;
import org.evd.game.runtime.support.function.ActorFunction2;
import org.evd.game.runtime.support.function.ActorFunction3;
import org.evd.game.runtime.support.function.ActorFunction4;
import org.evd.game.runtime.support.function.ActorFunction5;
import org.evd.game.runtime.support.function.ActorFunction6;
import org.evd.game.runtime.support.function.ActorFunction7;
import org.evd.game.runtime.support.function.ActorFunction8;
import org.evd.game.runtime.support.function.ActorFunction9;
import org.evd.game.runtime.support.function.ActorFunction10;
import org.evd.game.runtime.support.function.ActorReturnFunction1;
import org.evd.game.runtime.support.function.ActorReturnFunction2;
import org.evd.game.runtime.support.function.ActorReturnFunction3;
import org.evd.game.runtime.support.function.ActorReturnFunction4;
import org.evd.game.runtime.support.function.ActorReturnFunction5;
import org.evd.game.runtime.support.function.ActorReturnFunction6;
import org.evd.game.runtime.support.function.ActorReturnFunction7;
import org.evd.game.runtime.support.function.ActorReturnFunction8;
import org.evd.game.runtime.support.function.ActorReturnFunction9;
import org.evd.game.runtime.support.function.ActorReturnFunction10;

import java.lang.reflect.Constructor;
import java.util.Arrays;

public final class RpcMethodInvoker {
    private final Service service;
    private RPCImplBase methodFunctionProxy;
    private ClientCmdRegistryBase<?> clientCmdRegistry;

    public RpcMethodInvoker(Service service) {
        this.service = service;
    }

    public void dispatchClientCmd(int msgId, Object[] params) throws Exception {
        if (params == null || params.length != 2) {
            throw new IllegalArgumentException("client cmd params must be [ClientSessionRef, Chunk]: service="
                    + service.getId() + ", msgId=" + msgId);
        }
        if (!(params[0] instanceof ClientSessionRef session)) {
            throw new IllegalArgumentException("client cmd first param must be ClientSessionRef: service="
                    + service.getId() + ", msgId=" + msgId);
        }
        if (!(params[1] instanceof Chunk body)) {
            throw new IllegalArgumentException("client cmd second param must be Chunk: service="
                    + service.getId() + ", msgId=" + msgId);
        }
        clientCmdRegistry().dispatch(session, msgId, copyChunkBody(body));
    }

    Object invokeBusiness(int methodKey, Object[] args, ActorId actorId) throws InterruptedException {
        return invokeRpc(getMethodFunction(methodKey), args, actorId);
    }

    private Object getMethodFunction(int methodKey) {
        try {
            if (methodFunctionProxy == null) {
                Class<?> cls = Class.forName(service.getClass().getName() + "Impl");
                Constructor<?> constructor = cls.getDeclaredConstructor();
                constructor.setAccessible(true);
                methodFunctionProxy = (RPCImplBase) constructor.newInstance();
            }
            return methodFunctionProxy.getMethodFunction(service, methodKey);
        } catch (Exception e) {
            throw new SysException(e);
        }
    }

    private ClientCmdRegistryBase<?> clientCmdRegistry() {
        try {
            if (clientCmdRegistry == null) {
                Class<?> cls = Class.forName(service.getClass().getName() + "ClientCmdRegistry");
                Constructor<?> constructor = cls.getDeclaredConstructor(service.getClass());
                constructor.setAccessible(true);
                clientCmdRegistry = (ClientCmdRegistryBase<?>) constructor.newInstance(service);
            }
            return clientCmdRegistry;
        } catch (Exception e) {
            throw new SysException(e, "初始化客户端协议分发表失败: service={}", service.getId());
        }
    }

    private byte[] copyChunkBody(Chunk body) {
        return Arrays.copyOfRange(body.buffer, body.offset, body.offset + body.length);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object invokeRpc(Object func, Object[] args, ActorId actorId) throws InterruptedException {
        switch (args.length) {
            case 0:
                if (func instanceof ActorReturnFunction1 returnFunc) {
                    return returnFunc.apply(actorId);
                }
                if (func instanceof ActorFunction1 actorFunc) {
                    actorFunc.apply(actorId);
                    return null;
                }
                if (func instanceof ReturnFunction0 returnFunc) {
                    return returnFunc.apply();
                }
                ((Function0) func).apply();
                return null;
            case 1:
                if (func instanceof ActorReturnFunction2 returnFunc) {
                    return returnFunc.apply(actorId, args[0]);
                }
                if (func instanceof ActorFunction2 actorFunc) {
                    actorFunc.apply(actorId, args[0]);
                    return null;
                }
                if (func instanceof ReturnFunction1 returnFunc) {
                    return returnFunc.apply(args[0]);
                }
                ((Function1) func).apply(args[0]);
                return null;
            case 2:
                if (func instanceof ActorReturnFunction3 returnFunc) {
                    return returnFunc.apply(actorId, args[0], args[1]);
                }
                if (func instanceof ActorFunction3 actorFunc) {
                    actorFunc.apply(actorId, args[0], args[1]);
                    return null;
                }
                if (func instanceof ReturnFunction2 returnFunc) {
                    return returnFunc.apply(args[0], args[1]);
                }
                ((Function2) func).apply(args[0], args[1]);
                return null;
            case 3:
                if (func instanceof ActorReturnFunction4 returnFunc) {
                    return returnFunc.apply(actorId, args[0], args[1], args[2]);
                }
                if (func instanceof ActorFunction4 actorFunc) {
                    actorFunc.apply(actorId, args[0], args[1], args[2]);
                    return null;
                }
                if (func instanceof ReturnFunction3 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2]);
                }
                ((Function3) func).apply(args[0], args[1], args[2]);
                return null;
            case 4:
                if (func instanceof ActorReturnFunction5 returnFunc) {
                    return returnFunc.apply(actorId, args[0], args[1], args[2], args[3]);
                }
                if (func instanceof ActorFunction5 actorFunc) {
                    actorFunc.apply(actorId, args[0], args[1], args[2], args[3]);
                    return null;
                }
                if (func instanceof ReturnFunction4 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3]);
                }
                ((Function4) func).apply(args[0], args[1], args[2], args[3]);
                return null;
            case 5:
                if (func instanceof ActorReturnFunction6 returnFunc) {
                    return returnFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4]);
                }
                if (func instanceof ActorFunction6 actorFunc) {
                    actorFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4]);
                    return null;
                }
                if (func instanceof ReturnFunction5 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4]);
                }
                ((Function5) func).apply(args[0], args[1], args[2], args[3], args[4]);
                return null;
            case 6:
                if (func instanceof ActorReturnFunction7 returnFunc) {
                    return returnFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4], args[5]);
                }
                if (func instanceof ActorFunction7 actorFunc) {
                    actorFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4], args[5]);
                    return null;
                }
                if (func instanceof ReturnFunction6 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5]);
                }
                ((Function6) func).apply(args[0], args[1], args[2], args[3], args[4], args[5]);
                return null;
            case 7:
                if (func instanceof ActorReturnFunction8 returnFunc) {
                    return returnFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
                }
                if (func instanceof ActorFunction8 actorFunc) {
                    actorFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
                    return null;
                }
                if (func instanceof ReturnFunction7 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
                }
                ((Function7) func).apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
                return null;
            case 8:
                if (func instanceof ActorReturnFunction9 returnFunc) {
                    return returnFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
                }
                if (func instanceof ActorFunction9 actorFunc) {
                    actorFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
                    return null;
                }
                if (func instanceof ReturnFunction8 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
                }
                ((Function8) func).apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
                return null;
            case 9:
                if (func instanceof ActorReturnFunction10 returnFunc) {
                    return returnFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
                }
                if (func instanceof ActorFunction10 actorFunc) {
                    actorFunc.apply(actorId, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
                    return null;
                }
                if (func instanceof ReturnFunction9 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
                }
                ((Function9) func).apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
                return null;
            case 10:
                if (func instanceof ReturnFunction10 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9]);
                }
                ((Function10) func).apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9]);
                return null;
            default:
                return null;
        }
    }
}
