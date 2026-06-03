package org.evd.game.StageService.dbDef.db.table;

import org.evd.game.StageService.dbDef.db.bean.DBPlayerDataJSON;
import org.evd.game.StageService.dbDef.db._table_._DBPlayerDataJSONTable_;
import org.evd.game.runtime.Db.table.Mdb;
import org.evd.game.runtime.Db.table.TTable;
import org.evd.game.runtime.Service;

public final class DBPlayerDataJSONTable {
    private DBPlayerDataJSONTable() {
    }

    public static boolean add(String key, DBPlayerDataJSON value) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<String, DBPlayerDataJSON> tTable = mdb.getTTable(_DBPlayerDataJSONTable_.class);
        return tTable.add(key, value);
    }

    public static boolean add(String key, DBPlayerDataJSON value, boolean immediately) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<String, DBPlayerDataJSON> tTable = mdb.getTTable(_DBPlayerDataJSONTable_.class);
        return tTable.add(key, value, immediately);
    }

    public static DBPlayerDataJSON get(String key) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<String, DBPlayerDataJSON> tTable = mdb.getTTable(_DBPlayerDataJSONTable_.class);
        return tTable.get(key);
    }

    public static boolean remove(String key) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<String, DBPlayerDataJSON> tTable = mdb.getTTable(_DBPlayerDataJSONTable_.class);
        return tTable.remove(key);
    }
}
