package org.evd.game.StageService.db.table;

import org.evd.game.StageService.db.bean.DBPlayerData;
import org.evd.game.StageService.db._table_._DBPlayerDataTable_;
import org.evd.game.runtime.Db.table.Mdb;
import org.evd.game.runtime.Db.table.TTable;
import org.evd.game.runtime.Service;

public final class DBPlayerDataTable {
    private DBPlayerDataTable() {
    }

    public static boolean add(Long key, DBPlayerData value) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<Long, DBPlayerData> tTable = mdb.getTTable(_DBPlayerDataTable_.class);
        return tTable.add(key, value);
    }

    public static boolean add(Long key, DBPlayerData value, boolean immediately) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<Long, DBPlayerData> tTable = mdb.getTTable(_DBPlayerDataTable_.class);
        return tTable.add(key, value, immediately);
    }

    public static DBPlayerData get(Long key) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<Long, DBPlayerData> tTable = mdb.getTTable(_DBPlayerDataTable_.class);
        return tTable.get(key);
    }

    public static boolean remove(Long key) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<Long, DBPlayerData> tTable = mdb.getTTable(_DBPlayerDataTable_.class);
        return tTable.remove(key);
    }
}
