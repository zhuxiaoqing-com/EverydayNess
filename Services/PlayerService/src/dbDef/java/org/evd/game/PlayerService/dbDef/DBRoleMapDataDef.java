package org.evd.game.PlayerService.dbDef;

import org.evd.game.annotation.serialize.DBDirtyEntity;
import org.evd.game.annotation.serialize.DBDirtyTag;
import org.evd.game.annotation.serialize.DBserialize;

/** 玩家地图状态；地图转场中间态和当前地图都必须在 PlayerService 持久化。 */
@DBDirtyEntity(value = DBserialize.PB, table = true)
public class DBRoleMapDataDef {
    @DBDirtyTag(value = 1, primaryKey = true)
    private long playerId;
    @DBDirtyTag(2)
    private DBTransferContextDef transferContext;
    @DBDirtyTag(3)
    private DBMapInfoDef currMapInfo;
}
