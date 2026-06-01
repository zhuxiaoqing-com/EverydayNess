package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.SerializeClass;

/**
 * DB 请求操作类型。
 */
@SerializeClass
public enum DbOpType {
    /** 建表语句 */
    CREATE_TABLE,
    /** 查询单条数据。 */
    GET,
    /** 批量查询数据。 */
    BATCH_GET,
    /** 保存单条数据。 */
    SAVE,
    /** 批量保存数据。 */
    BATCH_SAVE,
    /** 删除单条数据。 */
    REMOVE,
    /** 批量删除数据。 */
    BATCH_REMOVE
}
