package org.evd.game.PlayerService.dbDef;

import org.evd.game.annotation.serialize.DBDirtyEntity;
import org.evd.game.annotation.serialize.DBDirtyTag;
import org.evd.game.annotation.serialize.DBserialize;

import java.util.List;

/** DBRoleMapData 中保存的当前匹配快照，用于超时、下线和再次匹配时校正状态。 */
@DBDirtyEntity(DBserialize.PB)
public class DBMatchContextDef {
    @DBDirtyTag(1)
    private int matchType;
    @DBDirtyTag(2)
    private List<Integer> mapCfgIds;
    @DBDirtyTag(3)
    private long matchStartMill;
    @DBDirtyTag(4)
    private List<Integer> dutyIds;
}
