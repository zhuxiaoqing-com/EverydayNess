package org.evd.game.PlayerService.login;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.dbDef.db.bean.DBPlayerData;
import org.evd.game.PlayerService.dbDef.db.table.DBPlayerDataTable;
import org.evd.game.PlayerService.event.RoleLoginEvent;
import org.evd.game.PlayerService.event.RoleMidnightEvent;
import org.evd.game.PlayerService.map.PlayerMapLogic;
import org.evd.game.PlayerService.player.PlayerDataRepository;
import org.evd.game.PlayerService.session.PlayerSessionManager;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.proto.RoleData;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.util.TimeUtils;
/** PlayerService 的玩家登录业务逻辑。 */
@Actor
public final class PlayerLoginLogic {
    /** 创建玩家运行态并加载玩家数据。 */
    public ActorAddress loginPlayer(String userId, RoleData role, ClientSessionRef session) {
        PlayerService owner = owner();
        PlayerSessionManager sessionManager = owner.sessionManager();
        if (userId == null || userId.isBlank() || role == null || role.getPlayerId() <= 0L
                || session == null || session.getGate() == null || session.getSessionId() <= 0L) {
            LogCore.core.warn("PlayerService 玩家登录参数非法: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                    owner.getId(), userId, role == null ? 0L : role.getPlayerId(),
                    session == null ? null : session.getGate(),
                    session == null ? 0L : session.getSessionId());
            return null;
        }
        long playerId = role.getPlayerId();
        if (sessionManager.hasOnlinePlayer(playerId)) {
            LogCore.core.error("PlayerService 玩家已经上线: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId);
            return null;
        }
        if (owner.hasPlayerActor(playerId)) {
            LogCore.core.error("PlayerService 玩家 Actor 已存在，拒绝重复上线: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId);
            return null;
        }

        ActorAddress actorAddress = owner.registerPlayerActor(playerId);
        sessionManager.bindPlayerSession(userId, playerId, session, actorAddress);
        if (!sessionManager.markReadyIfCurrent(userId, playerId, session)) {
            LogCore.core.warn("PlayerService 玩家数据加载完成后绑定状态失效: service={}, userId={}, playerId={}, gateSessionId={}",
                    owner.getId(), userId, playerId, session.getSessionId());
            return null;
        }
        LogCore.core.info("PlayerService 绑定玩家: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                owner.getId(), userId, playerId, session.getGate(), session.getSessionId());
        return actorAddress;
    }


    /** 登记 GW 玩家 ActorAddress；玩家不存在时直接跳过。 */
    public void bindGateActorAddress(long playerId, ActorAddress gateActorAddress) {
        PlayerService owner = owner();
        PlayerSessionManager sessionManager = owner.sessionManager();
        if (!sessionManager.hasOnlinePlayer(playerId)) {
            return;
        }
        sessionManager.bindGateActorAddress(playerId, gateActorAddress);
        owner.getMessageLocationSender().cache(ActorId.gate(playerId), gateActorAddress);
        LogCore.core.info("PlayerService 缓存 GWActorAddress: service={}, playerId={}, actorId={}, actorAddress={}",
                owner.getId(), playerId, ActorId.gate(playerId), gateActorAddress);
    }

    /** 完成进入地图后的玩家上线处理；地图进入本身由 PlayerMapLogic 负责。 */
    public void onlinePlayer(String userId, long playerId, RoleData role, ClientSessionRef session) {
        PlayerService owner = owner();
        PlayerSessionManager sessionManager = owner.sessionManager();
        if (!sessionManager.isCurrent(userId, playerId, session)) {
            LogCore.core.warn("PlayerService 玩家绑定不存在，跳过进入地图: service={}, userId={}, playerId={}",
                    owner.getId(), userId, playerId);
            return;
        }

        /*
         * 放这里比较合适，先让流程跑完 再加载，再次上线 还是会在这里排队 也没事;
         */
        try {
            Service.getCurrent().getMdb().loadPlayerAllTableToMemory(playerId, userId);
        } catch (Exception e) {
            LogCore.core.error("PlayerService MDB 加载失败: playerId={}", playerId, e);
            throw e;
        }

        if (!sessionManager.isCurrent(userId, playerId, session)) {
            LogCore.core.warn("PlayerService MDB 加载返回后登录 Session 已失效: service={}, userId={}, playerId={}, gateSessionId={}",
                    owner.getId(), userId, playerId, session.getSessionId());
            return;
        }

        PlayerDataRepository playerDataRepository = owner.playerDataRepository();
        playerDataRepository.loadOrCreate(playerId, role.getName(), role.getLevel());


        Service.getCurrent().publishEvent(RoleLoginEvent.Listener.class,
                new RoleLoginEvent(playerId), RoleLoginEvent.Listener::onEvent);
        DBPlayerData dbPlayerData = DBPlayerDataTable.get(playerId);
        long currMill = Service.getTime();
        if (!TimeUtils.isSameDay(dbPlayerData.getLastMidnightMill(), currMill)) {
            dbPlayerData.setLastMidnightMill(currMill);
            Service.getCurrent().publishEvent(RoleMidnightEvent.Listener.class,
                    new RoleMidnightEvent(playerId), RoleMidnightEvent.Listener::onEvent);
        }

        if (!sessionManager.markOnline(playerId)) {
            LogCore.core.warn("PlayerService 玩家进入地图后绑定状态已失效: service={}, userId={}, playerId={}, gateSessionId={}",
                    owner.getId(), userId, playerId, session.getSessionId());
            return;
        }
        LogCore.core.info("PlayerService 玩家正式上线: service={}, userId={}, playerId={}, gate={}, gateSessionId={}",
                owner.getId(), userId, playerId, session.getGate(), session.getSessionId());


        try {
            owner.getActor(PlayerMapLogic.class).enterMap(playerId);
        } catch (RuntimeException e) {
            LogCore.core.warn("PlayerService 玩家进入地图失败: service={}, userId={}, playerId={}, message={}",
                    owner.getId(), userId, playerId, e.getMessage());
            return;
        }
    }

    private PlayerService owner() {
        return Service.getCurrent(PlayerService.class);
    }
}
