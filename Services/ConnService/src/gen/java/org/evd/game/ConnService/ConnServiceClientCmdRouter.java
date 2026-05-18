package org.evd.game.ConnService;

import org.evd.game.common.proto.MsgId;
import org.evd.game.runtime.Chunk;
import org.evd.game.runtime.ClientSessionRef;
import org.evd.game.runtime.DistributeConfig;
import org.evd.game.runtime.Session;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.common.proxy.StageServiceProxy;
import org.evd.game.common.proxy.ConnServiceProxy;

/**
 * 根据所有客户端协议分发表聚合生成的总路由
 */
public final class ConnServiceClientCmdRouter {
    private ConnServiceClientCmdRouter() {
    }

    public static void forward(ConnService owner, Session session, int cmd, byte[] body) {
        ClientSessionRef sessionRef = owner.buildClientSessionRef(session);
        switch (cmd) {
            case MsgId.C2S_LOGIN_VALUE:
                forwardToStageService(sessionRef, cmd, body);
                return;
            case MsgId.C2S_CONN_PING_VALUE:
                forwardToConnService(sessionRef, cmd, body);
                return;
            default:
                throw new IllegalStateException("未注册的客户端协议: cmd=" + cmd);
        }
    }

    private static void forwardToStageService(ClientSessionRef session, int cmd, byte[] body) {
        CallPoint callPoint = DistributeConfig.getNodeByServiceClass("org.evd.game.StageService.StageService", session.getRouteKey());
        if (callPoint == null) {
            throw new IllegalStateException("找不到客户端协议目标服务: cmd=1001, service=org.evd.game.StageService.StageService");
        }
        StageServiceProxy.inst(callPoint).forwardClientCmd(session, cmd, new Chunk(body));
    }

    private static void forwardToConnService(ClientSessionRef session, int cmd, byte[] body) {
        CallPoint callPoint = DistributeConfig.getNodeByServiceClass("org.evd.game.ConnService.ConnService", session.getRouteKey());
        if (callPoint == null) {
            throw new IllegalStateException("找不到客户端协议目标服务: cmd=1002, service=org.evd.game.ConnService.ConnService");
        }
        ConnServiceProxy.inst(callPoint).forwardClientCmd(session, cmd, new Chunk(body));
    }

}
