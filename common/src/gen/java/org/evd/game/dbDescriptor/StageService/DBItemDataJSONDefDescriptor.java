package org.evd.game.dbDescriptor.StageService;

import org.evd.game.annotation.DBserialize;
import org.evd.game.dbDescriptor.DbDescriptorOp;
import org.evd.game.dbDescriptor.DbEntityDescriptor;
import org.evd.game.dbDescriptor.DbRequestShape;
import org.evd.game.dbDescriptor.DbReturnShape;

/**
 * Generated descriptor for StageService DBItemDataJSONDef.
 */
public final class DBItemDataJSONDefDescriptor {
    public static final DbEntityDescriptor INSTANCE = DbEntityDescriptor.builder(
                    "StageService",
                    "DBItemDataJSONDef",
                    "org.evd.game.StageService.dbEntity.DBItemDataJSONDef",
                    "DBItemDataJSONDef",
                    DBserialize.JSON,
                    false)
            .addField(1, "itemSrl", "long", false, "", null)
            .addField(2, "itemId", "int", false, "", null)
            .addField(3, "itemName", "java.lang.String", false, "", null)
            .operation(DbDescriptorOp.SAVE, DbRequestShape.FIELD_ROW, DbReturnShape.AFFECTED_ROWS)
            .operation(DbDescriptorOp.BATCH_SAVE, DbRequestShape.FIELD_ROW_LIST, DbReturnShape.AFFECTED_ROWS)
            .build();

    private DBItemDataJSONDefDescriptor() {
    }
}
