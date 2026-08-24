package org.evd.game.LobbyService;

import org.evd.game.LobbyService.account.LobbyUserAccountRepository;
import org.evd.game.LobbyService.dbDef.db.bean.LBRole;
import org.evd.game.LobbyService.routing.LobbyLoadBalancerActor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.serializeBean.LobbyService.role.LobbyRoleSnapshot;
import org.evd.game.common.serializeBean.LobbyService.login.LobbyUserAccessResult;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.serializeBean.TickTimer;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.support.LogCore;

public class LobbyService extends Service {
    private final LobbyUserAccountRepository userAccountRepository;

    public LobbyService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        this.userAccountRepository = new LobbyUserAccountRepository();
    }

    /** 返回 Lobby 用户账号仓库。 */
    public LobbyUserAccountRepository userAccountRepository() {
        return userAccountRepository;
    }

    /** 校验用户账号；首登用户创建账号，封禁账号不得进入 OnlineService。 */
    @Rpc
    public LobbyUserAccessResult validateOrCreateUser(String userId) {
        return userAccountRepository.validateOrCreate(userId, getTimeCurrent());
    }

    public LobbyLoadBalancerActor loadBalancerActor() {
        return getActor(LobbyLoadBalancerActor.class);
    }

    @Rpc
    public LobbyRoleSnapshot getRole(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        LBRole role = userAccountRepository.loadRole(userId);
        if (role == null) {
            return null;
        }
        return new LobbyRoleSnapshot(role.getPlayerId(), role.getCharacterId(), role.getName(), role.getLevel());
    }

    /** 接收角色正式上线通知，确认上线角色仍属于当前账号。 */
    @Rpc
    public void playerOnline(String userId, long playerId, CallPoint gate, long gateSessionId) {
        LobbyRoleSnapshot role = getRole(userId);
        if (role == null || role.getPlayerId() != playerId) {
            LogCore.core.warn("LobbyService 忽略非法角色正式上线通知: userId={}, playerId={}, gate={}, gateSessionId={}",
                    userId, playerId, gate, gateSessionId);
            return;
        }
        LogCore.core.info("LobbyService 角色正式上线: userId={}, playerId={}, gate={}, gateSessionId={}",
                userId, playerId, gate, gateSessionId);
    }


    @Override
    protected void init_t() {
        super.init_t();

    }

    TickTimer tickTimer = new TickTimer(5000);

    @Override
    public void tick() {
        super.tick();
        if (!tickTimer.isPeriod(getTimeCurrent())) {
            return;
        }

     /*   launchCoroutine(() -> {
            int onlineCount = PlayerServiceProxy.inst().getOnlineCount(node.getAnyCallPointByType(ServiceType.PLAYER));
        });

        launchCoroutine(() -> {
            System.out.println("---");
        });
        launchCoroutine(() -> {
            ContinuationLockScope continuationLockScope = awaitCoroutineLockScope(LockType.ACTOR, new Object());
            ContinuationLockScope a = awaitCoroutineLockScope(LockType.ACTOR, new Object());
        });

        launchCoroutine(() -> {
                logCoroutineDebugDump("shutdown timeout");
        });*/
    }
}
