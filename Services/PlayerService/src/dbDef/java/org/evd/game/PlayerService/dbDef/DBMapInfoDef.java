package org.evd.game.PlayerService.dbDef;

import org.evd.game.annotation.serialize.DBDirtyEntity;
import org.evd.game.annotation.serialize.DBDirtyTag;
import org.evd.game.annotation.serialize.DBserialize;

/** DBRoleMapData 中保存的地图信息。 */
@DBDirtyEntity(DBserialize.PB)
public class DBMapInfoDef {
    @DBDirtyTag(1)
    private long sceneId;
    @DBDirtyTag(2)
    private int mapCfgId;
    @DBDirtyTag(3)
    private long groupId;
}
