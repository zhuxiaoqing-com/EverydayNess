package org.evd.game.runtime.Db.serialize;

import org.evd.game.annotation.serialize.SerializeClass;

/**
 * DB 主键值类型。
 */
@SerializeClass
public enum DbKeyType {
    /** long 主键。 */
    LONG,
    /** int 主键。 */
    INT,
    /** String 主键。 */
    STRING
}
