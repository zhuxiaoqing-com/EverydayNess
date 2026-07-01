package org.evd.game.StageService.dbDef.demo;

import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.Db.serialize.DbTableField;
import org.evd.game.runtime.Db.serialize.DbValue;
import org.evd.game.runtime.Db.serialize.MysqlReq;
import org.evd.game.runtime.Db.serialize.MysqlRsp;
import org.evd.game.runtime.Db.serialize.MysqlTableMeta;
import org.evd.game.runtime.Db.table.TTable;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class _DBPlayerPbTable_ extends TTable<Long, DBPlayer> {
	private static final String TABLE_NAME = "db_player";
	private static final String KEY_COLUMN_NAME = "k";
	private static final List<String> COLUMN_NAMES = List.of("k", "v");
	private static final String CREATE_TABLE_SQL = """
			CREATE TABLE IF NOT EXISTS db_player (
			    k BIGINT NOT NULL PRIMARY KEY,
			    v MEDIUMBLOB NOT NULL
			) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI
			""";
	private static final Schema<DBPlayer> SCHEMA = RuntimeSchema.getSchema(DBPlayer.class);

	private _DBPlayerPbTable_() {
	}

	@Override
	public String getName() {
		return TABLE_NAME;
	}

	@Override
	protected DBPlayer newValue() {
		return new DBPlayer();
	}

	public DBReq createCreateTableDBReq() {
		return createInitReq(CREATE_TABLE_SQL, new ArrayList<>());
	}

	public DBReq createGetDBReq(Long key) {
		return createReq(DbOpType.GET, List.of(createKeyField(key)));
	}

	public DBReq createSaveDBReq(DBPlayer player) {
		return createReq(DbOpType.SAVE, List.of(toSaveField(player.getId(), player)));
	}

	public DBReq createRemoveDBReq(Long key) {
		return createReq(DbOpType.REMOVE, List.of(createKeyField(key)));
	}

	public DBReq createBatchGetDBReq(Map<Long, DBPlayer> map) {
		return createReq(DbOpType.BATCH_GET, toKeyFieldList(map));
	}

	public DBReq createBatchSaveDBReq(Map<Long, DBPlayer> map) {
		requireBatchMap(map);
		List<DbTableField> tableFieldList = new ArrayList<>(map.size());
		for (Map.Entry<Long, DBPlayer> entry : map.entrySet()) {
			tableFieldList.add(toSaveField(entry.getKey(), entry.getValue()));
		}
		return createReq(DbOpType.BATCH_SAVE, tableFieldList);
	}

	public DBReq createBatchRemoveDBReq(Map<Long, DBPlayer> map) {
		return createReq(DbOpType.BATCH_REMOVE, toKeyFieldList(map));
	}

	public DBPlayer parseGetDBRsp(DBRsp rsp) {
		MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
		List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();
		if (tableFieldList == null || tableFieldList.isEmpty()) {
			return null;
		}
		return parseRow(tableFieldList.get(0));
	}

	public Map<Long, DBPlayer> parseBatchGetDBRsp(DBRsp rsp) {
		MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
		Map<Long, DBPlayer> result = new LinkedHashMap<>();
		for (DbTableField tableField : mysqlRsp.getTablFieldList()) {
			DBPlayer player = parseRow(tableField);
			if (player == null) {
				Long key = readRowKey(tableField);
				if (key != null) {
					result.put(key, null);
				}
				continue;
			}
			result.put(player.getId(), player);
		}
		return result;
	}

	private DBReq createInitReq(String sql, List<DbTableField> tableFieldList) {
		MysqlReq mysqlReq = new MysqlReq();
		mysqlReq.setTableName(TABLE_NAME);
		MysqlTableMeta tableMeta = new MysqlTableMeta();
		tableMeta.setKeyColumnName(KEY_COLUMN_NAME);
		tableMeta.setColumnNames(COLUMN_NAMES);
		mysqlReq.setTableMeta(tableMeta);
		mysqlReq.setSql(sql);
		mysqlReq.setTablFieldList(tableFieldList);

		DBReq dbReq = new DBReq();
		dbReq.setDbOpType(DbOpType.CREATE_TABLE);
		dbReq.setMysqlReq(mysqlReq);
		return dbReq;
	}

	private DBReq createReq(DbOpType opType, List<DbTableField> tableFieldList) {
		MysqlReq mysqlReq = new MysqlReq();
		mysqlReq.setTableName(TABLE_NAME);
		mysqlReq.setTablFieldList(tableFieldList);

		DBReq dbReq = new DBReq();
		dbReq.setDbOpType(opType);
		dbReq.setMysqlReq(mysqlReq);
		return dbReq;
	}

	private DbTableField createKeyField(Long key) {
		Objects.requireNonNull(key, "key 不能为空");
		return new DbTableField(List.of(new DbValue(key)));
	}

	private List<DbTableField> toKeyFieldList(Map<Long, DBPlayer> map) {
		requireBatchMap(map);
		List<DbTableField> tableFieldList = new ArrayList<>(map.size());
		for (Long key : map.keySet()) {
			tableFieldList.add(createKeyField(key));
		}
		return tableFieldList;
	}

	private DbTableField toSaveField(Long key, DBPlayer player) {
		Objects.requireNonNull(player, "player 不能为空");
		List<DbValue> valueList = new ArrayList<>(2);
		valueList.add(new DbValue(key));
		valueList.add(new DbValue(encodePlayer(player)));
		return new DbTableField(valueList);
	}

	private DBPlayer parseRow(DbTableField tableField) {
		List<DbValue> valueList = tableField.getValueList();
		if (valueList == null || valueList.size() != 2) {
			return null;
		}
		long key = ((Number) valueList.get(0).getV()).longValue();
		DBPlayer player = decodePlayer(key, (byte[]) valueList.get(1).getV());
		player.dirty = false;
		return player;
	}

	private Long readRowKey(DbTableField tableField) {
		if (tableField == null || tableField.getValueList() == null || tableField.getValueList().isEmpty()) {
			return null;
		}
		Object key = tableField.getValueList().get(0).getV();
		return key instanceof Number ? ((Number) key).longValue() : null;
	}

	private byte[] encodePlayer(DBPlayer player) {
		Objects.requireNonNull(player, "player 不能为空");
		LinkedBuffer buffer = LinkedBuffer.allocate();
		try {
			return ProtostuffIOUtil.toByteArray(player, SCHEMA, buffer);
		} finally {
			buffer.clear();
		}
	}

	private DBPlayer decodePlayer(long key, byte[] bytes) {
		DBPlayer player = new DBPlayer();
		if (bytes != null && bytes.length > 0) {
			ProtostuffIOUtil.mergeFrom(bytes, player, SCHEMA);
		}
		player.setId(key);
		player.dirty = false;
		return player;
	}

	private MysqlRsp requireMysqlRsp(DBRsp rsp) {
		Objects.requireNonNull(rsp, "rsp 不能为空");
		if (!rsp.isSuccess()) {
			throw new IllegalArgumentException("db rsp fail: " + rsp.getExceptionMessage());
		}
		MysqlRsp mysqlRsp = rsp.getMysqlRsp();
		if (mysqlRsp == null) {
			throw new IllegalArgumentException("db rsp mysqlRsp 不能为空");
		}
		return mysqlRsp;
	}

	private void requireBatchMap(Map<Long, DBPlayer> map) {
		if (map == null || map.isEmpty()) {
			throw new IllegalArgumentException("batch map 不能为空");
		}
	}

}
