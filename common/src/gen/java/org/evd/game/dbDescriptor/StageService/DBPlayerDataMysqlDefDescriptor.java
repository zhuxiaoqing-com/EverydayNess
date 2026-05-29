package org.evd.game.dbDescriptor.StageService;

import org.evd.game.annotation.DBserialize;
import org.evd.game.dbDescriptor.DbDescriptorOp;
import org.evd.game.dbDescriptor.DbEntityDescriptor;
import org.evd.game.dbDescriptor.DbRequestShape;
import org.evd.game.dbDescriptor.DbReturnShape;

/**
 * Generated descriptor for StageService DBPlayerDataMysqlDef.
 */
public final class DBPlayerDataMysqlDefDescriptor {
    public static final DbEntityDescriptor INSTANCE = DbEntityDescriptor.builder(
                    "StageService",
                    "DBPlayerDataMysqlDef",
                    "org.evd.game.StageService.dbEntity.DBPlayerDataMysqlDef",
                    "DBPlayerDataMysqlDef",
                    DBserialize.MYSQL,
                    false)
            .addField(1, "id", "java.lang.String", true, "", null)
            .addField(2, "name", "java.lang.String", false, "", null)
            .addField(3, "lv", "int", false, "", null)
            .addField(4, "intIntMap", "java.util.Map<java.lang.Integer, java.lang.Integer>", false, "", null)
            .addField(5, "intList", "java.util.List<java.lang.Integer>", false, "", null)
            .addField(6, "intSet", "java.util.Set<java.lang.Integer>", false, "", null)
            .addField(7, "intDBItemMap", "java.util.Map<java.lang.Integer, org.evd.game.StageService.dbEntity.DBItemDataMysqlDef>", false, "", "DBItemDataMysqlDef")
            .operation(DbDescriptorOp.GET, DbRequestShape.DB_KEY, DbReturnShape.FIELD_ROW)
            .operation(DbDescriptorOp.BATCH_GET, DbRequestShape.DB_KEY_LIST, DbReturnShape.FIELD_ROW_LIST)
            .operation(DbDescriptorOp.SAVE, DbRequestShape.FIELD_ROW, DbReturnShape.AFFECTED_ROWS)
            .operation(DbDescriptorOp.BATCH_SAVE, DbRequestShape.FIELD_ROW_LIST, DbReturnShape.AFFECTED_ROWS)
            .operation(DbDescriptorOp.REMOVE, DbRequestShape.DB_KEY, DbReturnShape.AFFECTED_ROWS)
            .operation(DbDescriptorOp.BATCH_REMOVE, DbRequestShape.DB_KEY_LIST, DbReturnShape.AFFECTED_ROWS)
            .operation(DbDescriptorOp.INIT_SCHEMA, DbRequestShape.SCHEMA, DbReturnShape.NONE)
            .build();

    private DBPlayerDataMysqlDefDescriptor() {
    }
}
