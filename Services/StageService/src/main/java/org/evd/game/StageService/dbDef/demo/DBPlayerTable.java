package org.evd.game.StageService.dbDef.demo;


import org.evd.game.runtime.Db.table.Mdb;
import org.evd.game.runtime.Db.table.TTable;
import org.evd.game.runtime.Service;

public class DBPlayerTable {
	private DBPlayerTable() {
	}

	public static boolean add(Long key, DBPlayer value) {
		Mdb mdb = Service.getCurrent().getMdb();
		TTable<Long, DBPlayer> tTable = mdb.getTTable(_DBPlayerTable_.class);
		return tTable.add(key, value);
	}

	public static boolean add(Long key, DBPlayer value, boolean immediately) {
		Mdb mdb = Service.getCurrent().getMdb();
		TTable<Long, DBPlayer> tTable = mdb.getTTable(_DBPlayerTable_.class);
		return tTable.add(key, value, immediately);
	}

	public static DBPlayer get(Long key) {
		Mdb mdb = Service.getCurrent().getMdb();
		TTable<Long, DBPlayer> tTable = mdb.getTTable(_DBPlayerTable_.class);
		return tTable.get(key);
	}

	public static boolean remove(Long key) {
		Mdb mdb = Service.getCurrent().getMdb();
		TTable<Long, DBPlayer> tTable = mdb.getTTable(_DBPlayerTable_.class);
		return tTable.remove(key);
	}

}
