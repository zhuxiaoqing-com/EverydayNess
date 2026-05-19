package org.evd.game.common.proxy;

import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.mailbox.MailboxKey;

/**
 * 手写 actor-location 专用代理。
 * location 命中宿主后，这里走运行时的 mailbox-forward 协议，而不是业务 service 包装方法。
 */
public class StageServiceActorLocationProxy extends RPCProxyBase {
    private StageServiceActorLocationProxy(CallPoint callPoint) {
        this.remote = callPoint;
    }

    public static StageServiceActorLocationProxy inst(CallPoint callPoint) {
        return new StageServiceActorLocationProxy(callPoint);
    }

    public void callHaHaHaActorRpc1(MailboxKey mailboxKey, int a, int b) {
        Service service = Service.getCurrent();
        service.locationCallWait(remote, mailboxKey, HaHaHaActorProxy.EnumCall.ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT, new Object[]{a, b});
    }

    public void callHaHaHaActorRpc2(MailboxKey mailboxKey, Object a, Object b) {
        Service service = Service.getCurrent();
        service.locationCallWait(remote, mailboxKey, HaHaHaActorProxy.EnumCall.ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT, new Object[]{a, b});
    }
}
