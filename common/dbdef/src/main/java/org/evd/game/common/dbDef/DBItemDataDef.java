package org.evd.game.common.dbDef;

import org.evd.game.annotation.DBDirtyEntity;
import org.evd.game.annotation.DBDirtyTag;
import org.evd.game.annotation.DBserialize;

/**
 * 公共 PB 数据定义，供所有 Service 的 dbDef 复用。
 */
@DBDirtyEntity(value = DBserialize.PB)
public class DBItemDataDef {
    @DBDirtyTag(1)
    private long id;
    @DBDirtyTag(2)
    private String name;
}
