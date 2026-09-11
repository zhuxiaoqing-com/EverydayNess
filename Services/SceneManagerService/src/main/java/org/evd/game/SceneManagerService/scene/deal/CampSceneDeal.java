package org.evd.game.SceneManagerService.scene.deal;

import org.evd.game.SceneManagerService.SceneManagerService;
import org.evd.game.SceneManagerService.scene.AbstractSceneDeal;

/** 阵营地图 Deal。具体场景类型由 Stage 根据 mapType 创建。 */
public final class CampSceneDeal extends AbstractSceneDeal {
    public CampSceneDeal(SceneManagerService owner) {
        super(owner);
    }
}
