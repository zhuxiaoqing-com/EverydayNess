package org.evd.game.StageService.dbEntity;

import org.evd.game.annotation.DBDirtyEntity;
import org.evd.game.annotation.DBDirtyTag;
import org.evd.game.annotation.DBserialize;

/**
 * @author zhuxiaoqing
 * @Description: Item
 * @Date 2026/5/21 20:49
 **/
@DBDirtyEntity(DBserialize.MYSQL)
public class DBItemDataMysqlDef {
    @DBDirtyTag(1)
    private long itemSrl;
    @DBDirtyTag(2)
    private int itemId;
    @DBDirtyTag(3)
    private String itemName;

}
