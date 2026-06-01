package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.SerializeClass;

/**
 * DB 数据载体类型。
 */
@SerializeClass
public enum DbDataType {
    /** KV + PB 二进制值。 */
    KV_PB,
    /** KV + JSON 字符串值。 */
    KV_JSON,
    /** MySQL 行字段集合。 */
    MYSQL_ROW,
    /** Mongo 文档结构。 */
    MONGO_DOC
}
