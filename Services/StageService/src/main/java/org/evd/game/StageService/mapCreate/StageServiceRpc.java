package org.evd.game.StageService.mapCreate;

import org.evd.game.StageService.StageService;
import org.evd.game.StageService.scene.logic.BuffLogic;
import org.evd.game.StageService.scene.logic.MonsterLogic;
import org.evd.game.StageService.scene.logic.SkillLogic;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.common.serializeBean.SceneManagerService.routing.PlayerEnterRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapKey;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapInfo;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SMapCreateRequest;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SRunningMapInfo;
import org.evd.game.runtime.Service;
import java.util.List;

/** StageService 地图创建和进入 RPC 入口。 */
@Actor
@RpcHandler
public final class StageServiceRpc {
    @Rpc
    public List<SMapInfo> getMaps() {
        return logic().getMaps();
    }

    @Rpc
    public int getMapCount() {
        return logic().getMapCount();
    }

    @Rpc
    public long spawnMonster(long sceneId, int monsterConfigId) {
        return monsterLogic().spawnMonster(sceneId, monsterConfigId);
    }

    @Rpc
    public boolean useSkill(long sceneId, long casterId, int skillId, int level, long targetId) {
        return skillLogic().useSkill(logic().getScene(sceneId), casterId, skillId, level, targetId,
                Service.getTime());
    }

    @Rpc
    public boolean addBuff(long sceneId, long targetId, int buffId) {
        return buffLogic().addBuff(logic().getScene(sceneId), targetId, buffId, Service.getTime());
    }

    @Rpc
    public SRunningMapInfo getRunningMapInfo(long sceneId) {
        return logic().getRunningMapInfo(sceneId);
    }

    @Rpc
    public boolean createScene(SMapKey mapKey, long sceneId) {
        return logic().createScene(mapKey, sceneId);
    }

    @Rpc
    public boolean createScene(SMapCreateRequest request, long sceneId) {
        return logic().createScene(request, sceneId);
    }

    @Rpc
    public boolean prepareEnterScene(PlayerEnterRequest request) {
        return logic().prepareEnterScene(request);
    }

    @Rpc
    public void enterScene(long sceneId, SPlayerMapData playerData) {
        logic().enterScene(sceneId, playerData);
    }

    @Rpc
    public boolean exitScene(long sceneId, long playerId) {
        return logic().exitScene(sceneId, playerId);
    }

    @Rpc
    public boolean destroyScene(long sceneId) {
        return logic().destroyScene(sceneId);
    }

    private StageSceneLogic logic() {
        return Service.getCurrent(StageService.class).getActor(StageSceneLogic.class);
    }

    private MonsterLogic monsterLogic() {
        return Service.getCurrent(StageService.class).getActor(MonsterLogic.class);
    }

    private SkillLogic skillLogic() {
        return Service.getCurrent(StageService.class).getActor(SkillLogic.class);
    }

    private BuffLogic buffLogic() {
        return Service.getCurrent(StageService.class).getActor(BuffLogic.class);
    }
}
