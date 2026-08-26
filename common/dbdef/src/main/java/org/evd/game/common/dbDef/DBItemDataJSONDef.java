package org.evd.game.common.dbDef;

import org.evd.game.annotation.serialize.DBDirtyEntity;
import org.evd.game.annotation.serialize.DBDirtyTag;
import org.evd.game.annotation.serialize.DBserialize;

/**
 * 公共 JSON 数据定义，供所有 Service 的 dbDef 复用。
 */
@DBDirtyEntity(value = DBserialize.JSON)
public class DBItemDataJSONDef {
    @DBDirtyTag(1)
    private long id;
    @DBDirtyTag(2)
    private String name;
}
