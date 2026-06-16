package org.evd.game.PlayerService.dbDef;

import org.evd.game.annotation.DBDirtyEntity;
import org.evd.game.annotation.DBDirtyTag;
import org.evd.game.annotation.DBserialize;

/**
 * @author zhuxiaoqing
 * @Description: DBItemData
 * @Date 2026/5/21 20:48
 **/
@DBDirtyEntity(value = DBserialize.PB)
public class DBItemDataDef {
    @DBDirtyTag(1)
    private long id;
    @DBDirtyTag(2)
    private String name;
}
