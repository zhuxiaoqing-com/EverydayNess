package org.evd.game.MatchService;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.MatchService.entity.team.MatchTeam;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.common.constant.MapConst;
import org.evd.game.common.proxy.PlayerService.PlayerMapRpcProxy;
import org.evd.game.common.proxy.SceneManagerService.SceneManagerRpcProxy;
import org.evd.game.common.serializeBean.MatchService.match.SMatchEnterParams;
import org.evd.game.common.serializeBean.MatchService.match.SMatchPlayer;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRobot;
import org.evd.game.common.serializeBean.MatchService.match.SMatchSceneCreateParams;
import org.evd.game.common.serializeBean.MatchService.match.SMatchScenePlayer;
import org.evd.game.common.serializeBean.SceneManagerService.routing.MatchPlayerEnterMapParam;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 匹配场景逻辑：组装 Robot、创建地图并通知真实玩家转场。 */
@Slf4j
@Actor
public final class MatchSceneLogic {
    public SMapInfo createMatchScene(int mapCfgId, long groupId, long sceneId,
                                     List<MatchTeam> teams, Map<Long, Integer> teamCamps) {
        if (sceneId > 0L) {
            return new SMapInfo(sceneId, mapCfgId, groupId);
        }

        List<SMatchRobot> robots = createRobotParams(teams, teamCamps);
        List<SMatchScenePlayer> players = new ArrayList<>();
        for (MatchTeam team : teams) {
            if (team.isRobot()) {
                continue;
            }
            int camp = teamCamps.getOrDefault(team.getTeamId(), 0);
            for (SMatchPlayer player : team.getMembers()) {
                if (player.isRobot()) {
                    continue;
                }
                players.add(new SMatchScenePlayer(player.getPlayerId(), player.getPlayerService(),
                        new SMatchEnterParams(camp, player.getSelectedDuty(), robots)));
            }
        }

        if (MapConfigs.get(mapCfgId) == null) {
            log.error("MatchService 创建匹配地图失败，地图配置不存在: mapCfgId={}", mapCfgId);
            return null;
        }
        SMapCreateRequest request = new SMapCreateRequest(new SMapKey(mapCfgId, groupId),
                new SMatchSceneCreateParams(players, robots));
        CallPoint sceneManager = MapConst.getSceneManagerCallPoint(mapCfgId);
        RpcResult<SMapInfo> result = SceneManagerRpcProxy.callCreateScene(sceneManager, request);
        if (!result.isSuccess() || result.getValue() == null) {
            log.error("MatchService 创建匹配地图失败: mapCfgId={}, groupId={}, errorCode={}, message={}",
                    mapCfgId, groupId, result.getErrorCode(), result.getErrorMessage());
            return null;
        }
        return result.getValue();
    }

    public void createAndEnterMap(SMapInfo matchInfo, List<MatchTeam> teams,
                                  Map<Long, Integer> teamCamps) {
        Service.launchCurrentCoroutine(() -> {
            SMapInfo targetInfo = createMatchScene(
                    matchInfo.getMapCfgId(), matchInfo.getGroupId(), matchInfo.getSceneId(),
                    teams, teamCamps);
            if (targetInfo == null) {
                log.error("MatchService 匹配结果已确定，但地图创建失败: mapCfgId={}, groupId={}, teamCount={}",
                        matchInfo.getMapCfgId(), matchInfo.getGroupId(), teams.size());
                return;
            }
            dispatchMatch(targetInfo, teams, teamCamps);
        });
    }

    public void dispatchMatch(SMapInfo targetInfo, List<MatchTeam> teams,
                               Map<Long, Integer> teamCamps) {
        log.info("MatchService 匹配成功: mapCfgId={}, groupId={}, teamCount={}, memberCount={}, camps={}",
                targetInfo.getMapCfgId(), targetInfo.getGroupId(), teams.size(),
                teams.stream().mapToInt(MatchTeam::getMemberSize).sum(), teamCamps);
        for (MatchTeam team : teams) {
            for (SMatchPlayer player : team.getMembers()) {
                if (player.isRobot()) {
                    continue;
                }
                RpcResult<Void> result = PlayerMapRpcProxy.sendMatchEnterMap(
                        player.getPlayerService(), player.getPlayerId(), targetInfo,
                        new MatchPlayerEnterMapParam(
                                teamCamps.getOrDefault(team.getTeamId(), 0),
                                player.getSelectedDuty()));
                if (!result.isSuccess()) {
                    log.error("MatchService 通知 PlayerService 进入匹配地图失败: playerId={}, mapCfgId={}, groupId={}, errorCode={}, message={}",
                            player.getPlayerId(), targetInfo.getMapCfgId(), targetInfo.getGroupId(),
                            result.getErrorCode(), result.getErrorMessage());
                }
            }
        }
    }

    public List<SRunningMapInfo> getRunningMaps(int mapCfgId) {
        CallPoint sceneManager = MapConst.getSceneManagerCallPoint(mapCfgId);
        RpcResult<List<SRunningMapInfo>> result = SceneManagerRpcProxy.callGetRunningMaps(sceneManager, mapCfgId);
        if (!result.isSuccess() || result.getValue() == null) {
            log.error("MatchService 查询运行中地图失败: mapCfgId={}, errorCode={}, message={}",
                    mapCfgId, result.getErrorCode(), result.getErrorMessage());
            return List.of();
        }
        return result.getValue();
    }

    private List<SMatchRobot> createRobotParams(List<MatchTeam> teams,
                                                Map<Long, Integer> teamCamps) {
        List<SMatchRobot> result = new ArrayList<>();
        for (MatchTeam team : teams) {
            int camp = teamCamps.getOrDefault(team.getTeamId(), 0);
            for (SMatchPlayer player : team.getMembers()) {
                if (!player.isRobot()) {
                    continue;
                }
                result.add(new SMatchRobot(player.getPlayerId(), camp,
                        player.getSelectedDuty(), player.getName()));
            }
        }
        return result;
    }
}
