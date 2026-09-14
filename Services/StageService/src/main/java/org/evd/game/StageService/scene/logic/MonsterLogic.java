package org.evd.game.StageService.scene.logic;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.StageService.StageService;
import org.evd.game.StageService.scene.battle.BattleScene;
import org.evd.game.StageService.scene.movable.Monster;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.config.table.MapConfig;
import org.evd.game.common.config.table.MapConfigs;
import org.evd.game.common.config.table.MonsterConfig;
import org.evd.game.common.config.table.MonsterConfigs;
import org.evd.game.runtime.Service;

/** 负责怪物配置读取和怪物实体创建。 */
@Actor
@Slf4j
public final class MonsterLogic {
    public void loadConfiguredMonsters(BattleScene scene, int mapCfgId) {
        MapConfig config = MapConfigs.get(mapCfgId);
        if (config == null || config.getMonsterIds() == null) {
            return;
        }
        for (Integer monsterConfigId : config.getMonsterIds()) {
            if (monsterConfigId != null) {
                spawnMonster(scene, monsterConfigId);
            }
        }
    }

    public long spawnMonster(long sceneId, int monsterConfigId) {
        BattleScene scene = service().getActor(
                org.evd.game.StageService.mapCreate.StageSceneLogic.class).getScene(sceneId);
        if (scene == null) {
            log.warn("StageService 生成怪物失败，找不到场景: sceneId={}, monsterCfgId={}",
                    sceneId, monsterConfigId);
            return 0L;
        }
        Monster monster = spawnMonster(scene, monsterConfigId);
        return monster == null ? 0L : monster.getId();
    }

    private Monster spawnMonster(BattleScene scene, int monsterConfigId) {
        MonsterConfig config = MonsterConfigs.get(monsterConfigId);
        if (config == null) {
            log.error("BattleScene 生成怪物失败，找不到配置: sceneId={}, monsterCfgId={}",
                    scene.getSceneId(), monsterConfigId);
            return null;
        }
        Monster monster = new Monster(scene.allocateMonsterId(), config,
                service().getActor(BuffLogic.class), service().getActor(SkillLogic.class));
        scene.addMonster(monster);
        log.info("BattleScene 生成怪物: sceneId={}, monsterId={}, monsterCfgId={}",
                scene.getSceneId(), monster.getId(), monsterConfigId);
        return monster;
    }

    private StageService service() {
        return Service.getCurrent(StageService.class);
    }
}
