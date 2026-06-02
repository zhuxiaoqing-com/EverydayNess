package org.evd.game.StageService.db.table;

import org.evd.game.StageService.db.bean.DBPlayerDataMysql;
import org.evd.game.StageService.db._table_._DBPlayerDataMysqlTable_;
import org.evd.game.runtime.Db.table.Mdb;
import org.evd.game.runtime.Db.table.TTable;
import org.evd.game.runtime.Service;

public final class DBPlayerDataMysqlTable {
    private DBPlayerDataMysqlTable() {
    }

    public static boolean add(Integer key, DBPlayerDataMysql value) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<Integer, DBPlayerDataMysql> tTable = mdb.getTTable(_DBPlayerDataMysqlTable_.class);
        return tTable.add(key, value);
    }

    public static boolean add(Integer key, DBPlayerDataMysql value, boolean immediately) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<Integer, DBPlayerDataMysql> tTable = mdb.getTTable(_DBPlayerDataMysqlTable_.class);
        return tTable.add(key, value, immediately);
    }

    public static DBPlayerDataMysql get(Integer key) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<Integer, DBPlayerDataMysql> tTable = mdb.getTTable(_DBPlayerDataMysqlTable_.class);
        return tTable.get(key);
    }

    public static boolean remove(Integer key) {
        Mdb mdb = Service.getCurrent().getMdb();
        TTable<Integer, DBPlayerDataMysql> tTable = mdb.getTTable(_DBPlayerDataMysqlTable_.class);
        return tTable.remove(key);
    }
}
