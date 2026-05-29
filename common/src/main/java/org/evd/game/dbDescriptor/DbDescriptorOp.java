package org.evd.game.dbDescriptor;

/**
 * Descriptor 约定的数据库操作。
 */
public enum DbDescriptorOp {
    GET,
    BATCH_GET,
    SAVE,
    BATCH_SAVE,
    REMOVE,
    BATCH_REMOVE,
    INIT_SCHEMA
}
