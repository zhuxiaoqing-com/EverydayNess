package org.evd.game.common.config.table;

import lombok.Getter;
import org.evd.game.annotation.config.Config;

/** 场景基础配置。 */
@Config(file = "scene.csv", keys = {"sceneId"})
@Getter
public class SceneConfig {
    /** 场景配置 ID。 */
    private int sceneId;
    /** 场景名称。 */
    private String name;
    /** 场景类型。 */
    private int type;
    /** 场景人数限制。 */
    private int playerLimit;
}
