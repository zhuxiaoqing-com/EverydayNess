package org.evd.game.common.proxy;

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.mailbox.MailboxKey;

/**
* 根据HaHaHaActorService生成的代理类
*/
public class HaHaHaActorProxy extends RPCProxyBase {

    public final static class EnumCall{
        public final static int ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT = 6;
        public final static int ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT = 7;
    }

    private MailboxKey mailboxKey;

    private HaHaHaActorProxy(CallPoint callPoint, MailboxKey mailboxKey){
        this.remote = callPoint;
        this.mailboxKey = mailboxKey == null ? null : new MailboxKey(mailboxKey);
    }
    public static HaHaHaActorProxy inst(CallPoint callPoint, MailboxKey mailboxKey) {
        return new HaHaHaActorProxy(callPoint, mailboxKey);
    }

    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc1()
    */
    public void rpc1(int a, int b){
        Service service = Service.getCurrent();
        service.call(remote, mailboxKey, EnumCall.ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT, new Object[]{a, b});
    }
    /**
    * @see org.evd.game.StageService.HaHaHaActor#rpc2()
    */
    public void rpc2(Object a, Object b){
        Service service = Service.getCurrent();
        service.call(remote, mailboxKey, EnumCall.ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT, new Object[]{a, b});
    }
}
