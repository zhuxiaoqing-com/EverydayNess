package org.evd.game.PlayerService.dbDef;

import org.evd.game.annotation.serialize.DBDirtyEntity;
import org.evd.game.annotation.serialize.DBDirtyTag;
import org.evd.game.annotation.serialize.DBserialize;

/** DBRoleMapData 中保存的一次地图转场上下文。 */
@DBDirtyEntity(DBserialize.PB)
public class DBTransferContextDef {
    @DBDirtyTag(1)
    private long startMill;
    @DBDirtyTag(2)
    private boolean start;
    @DBDirtyTag(3)
    private DBMapInfoDef oldMapInfo;
    @DBDirtyTag(4)
    private DBMapInfoDef targetInfo;
}
