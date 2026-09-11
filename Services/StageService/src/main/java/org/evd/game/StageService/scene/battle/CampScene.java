package org.evd.game.StageService.scene.battle;

import org.evd.game.StageService.StageService;
import org.evd.game.common.serializeBean.MatchService.match.SMatchSceneCreateParams;
import org.evd.game.common.serializeBean.MatchService.match.SMatchScenePlayer;
import org.evd.game.common.serializeBean.MatchService.match.SMatchEnterParams;
import org.evd.game.common.serializeBean.MatchService.match.SMatchRobot;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;

/** 多人匹配/阵营场景，直接承载匹配创建数据和阵营玩法规则。 */
public final class CampScene extends BattleScene {
    private final SMatchSceneCreateParams createParams;

    public CampScene(SMapKey mapKey, long sceneId, StageService owner,
                     SMatchSceneCreateParams createParams) {
        super(mapKey, sceneId, owner);
        this.createParams = createParams == null ? null : new SMatchSceneCreateParams(createParams);
    }

    public SMatchSceneCreateParams getCreateParams() {
        return createParams == null ? null : new SMatchSceneCreateParams(createParams);
    }

    @Override
    protected int getPlayerCamp(long playerId) {
        if (createParams == null) return 0;
        return createParams.getPlayers().stream()
                .filter(player -> player.getPlayerId() == playerId)
                .map(SMatchScenePlayer::getEnterParams)
                .filter(java.util.Objects::nonNull)
                .mapToInt(SMatchEnterParams::getCamp)
                .findFirst().orElse(0);
    }

    @Override
    protected java.util.List<SMatchRobot> getSceneRobots() {
        return createParams == null ? java.util.List.of() : createParams.getRobots();
    }
}
