package org.evd.game.Db.serialize;

import org.evd.game.annotation.SerializeClass;

/**
 * 通用字段值类型。
 */
@SerializeClass
public enum DbValueType {
    /** long 值。 */
    LONG,
    /** int 值。 */
    INT,
    /** String 值。 */
    STRING,
    /** byte[] 值。 */
    BYTES,
    /** boolean 值。 */
    BOOLEAN,
    /** double 值。 */
    DOUBLE
}
