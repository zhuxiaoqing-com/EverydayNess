package org.evd.game.ConnService.login;

import org.evd.game.ConnService.ConnService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcHandler;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.serializeBean.ClientFrameChunk;

/** GW 首段登录：SDK 校验通过后向 OnlineService 申请登录准入。 */
@Actor
@RpcHandler
public final class ConnLoginRpc {
    /** Online 提交正式用户状态后，登记当前网关会话。 */
    @Rpc
    public boolean registerLogin(long sessionId, String userId) {
        return logic().registerLogin(sessionId, userId);
    }

    /** 绑定当前授权会话的玩家，并返回 GW 玩家 ActorAddress。 */
    @Rpc
    public ActorAddress bindPlayer(long sessionId, long playerId,
                                   ActorAddress playerActorAddress) {
        return logic().bindPlayer(sessionId, playerId, playerActorAddress);
    }

    /** 向待登录客户端返回失败响应并结束预登录连接。 */
    @Rpc
    public boolean rejectPendingLogin(long sessionId, String userId, String token,
                                      ClientFrameChunk packet, int brokenTypeCode, String reason) {
        return logic().rejectPendingLogin(sessionId, userId, token, packet, brokenTypeCode, reason);
    }

    private ConnLoginLogic logic() {
        return owner().getActor(ConnLoginLogic.class);
    }

    /** 获取当前执行此 RPC 的 ConnService 实例。 */
    private ConnService owner() {
        return Service.getCurrent(ConnService.class);
    }
}
