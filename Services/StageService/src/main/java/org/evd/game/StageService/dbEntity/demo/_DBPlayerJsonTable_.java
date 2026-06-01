package org.evd.game.StageService.dbEntity.demo;

import com.alibaba.fastjson2.JSON;
import org.evd.game.runtime.Db.collection.XArrayList;
import org.evd.game.runtime.Db.collection.XHashMap;
import org.evd.game.runtime.Db.collection.XHashSet;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.Db.serialize.DbTableField;
import org.evd.game.runtime.Db.serialize.DbValue;
import org.evd.game.runtime.Db.serialize.MysqlReq;
import org.evd.game.runtime.Db.serialize.MysqlRsp;
import org.evd.game.runtime.Db.table.TTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class _DBPlayerJsonTable_ extends TTable<Long, DBPlayer> {
	private static final String TABLE_NAME = "db_player_json";
	private static final String CREATE_TABLE_SQL = """
			CREATE TABLE IF NOT EXISTS db_player_json (
			    k BIGINT NOT NULL PRIMARY KEY,
			    v MEDIUMTEXT NOT NULL
			) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI
			""";
	private static final String GET_SQL = "SELECT k, v FROM " + TABLE_NAME + " WHERE k = ?";
	private static final String SAVE_SQL = "REPLACE INTO " + TABLE_NAME + " (k, v) VALUES (?, ?)";
	private static final String REMOVE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE k = ?";

	private _DBPlayerJsonTable_() {
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
		return createReq(DbOpType.CREATE_TABLE, CREATE_TABLE_SQL, new ArrayList<>());
	}

	public DBReq createGetDBReq(Long key) {
		return createReq(DbOpType.GET, GET_SQL, List.of(createKeyField(key)));
	}

	public DBReq createSaveDBReq(DBPlayer player) {
		return createReq(DbOpType.SAVE, SAVE_SQL, List.of(toSaveField(player.getId(), player)));
	}

	public DBReq createRemoveDBReq(Long key) {
		return createReq(DbOpType.REMOVE, REMOVE_SQL, List.of(createKeyField(key)));
	}

	public DBReq createBatchGetDBReq(Map<Long, DBPlayer> map) {
		return createReq(DbOpType.BATCH_GET, createBatchGetSql(map), toKeyFieldList(map));
	}

	public DBReq createBatchSaveDBReq(Map<Long, DBPlayer> map) {
		requireBatchMap(map);
		List<DbTableField> tableFieldList = new ArrayList<>(map.size());
		for (Map.Entry<Long, DBPlayer> entry : map.entrySet()) {
			tableFieldList.add(toSaveField(entry.getKey(), entry.getValue()));
		}
		return createReq(DbOpType.BATCH_SAVE, SAVE_SQL, tableFieldList);
	}

	public DBReq createBatchRemoveDBReq(Map<Long, DBPlayer> map) {
		return createReq(DbOpType.BATCH_REMOVE, createBatchRemoveSql(map), toKeyFieldList(map));
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
			result.put(player.getId(), player);
		}
		return result;
	}

	private DBReq createReq(DbOpType opType, String sql, List<DbTableField> tableFieldList) {
		MysqlReq mysqlReq = new MysqlReq();
		mysqlReq.setTableName(TABLE_NAME);
		mysqlReq.setSql(sql);
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
		valueList.add(new DbValue(JSON.toJSONString(toJsonValue(player))));
		return new DbTableField(valueList);
	}

	private DBPlayer parseRow(DbTableField tableField) {
		List<DbValue> valueList = tableField.getValueList();
		if (valueList == null || valueList.size() != 2) {
			throw new IllegalArgumentException("DBPlayerJson row column size error: " + (valueList == null ? 0 : valueList.size()));
		}
		long key = ((Number) valueList.get(0).getV()).longValue();
		PlayerJsonValue jsonValue = JSON.parseObject((String) valueList.get(1).getV(), PlayerJsonValue.class);
		DBPlayer player = fromJsonValue(key, jsonValue);
		player.dirty = false;
		return player;
	}

	private PlayerJsonValue toJsonValue(DBPlayer player) {
		PlayerJsonValue value = new PlayerJsonValue();
		value.name = requireNotNull(player.getName(), "name");
		value.lv = player.getLv();
		value.intIntMap = new LinkedHashMap<>(requireNotNull(player.getIntIntMap(), "intIntMap"));
		value.intList = new ArrayList<>(requireNotNull(player.getIntList(), "intList"));
		value.intSet = new java.util.LinkedHashSet<>(requireNotNull(player.getIntSet(), "intSet"));
		value.intDBItemMap = new LinkedHashMap<>();
		for (Map.Entry<Integer, DBItem> entry : requireNotNull(player.getIntDBItemMap(), "intDBItemMap").entrySet()) {
			value.intDBItemMap.put(entry.getKey(), toJsonItem(entry.getValue()));
		}
		return value;
	}

	private ItemJsonValue toJsonItem(DBItem item) {
		Objects.requireNonNull(item, "item 不能为空");
		ItemJsonValue value = new ItemJsonValue();
		value.itemSrl = item.getItemSrl();
		value.itemId = item.getItemId();
		value.itemName = requireNotNull(item.getItemName(), "itemName");
		return value;
	}

	private DBPlayer fromJsonValue(long key, PlayerJsonValue value) {
		PlayerJsonValue safeValue = value == null ? new PlayerJsonValue() : value;
		DBPlayer player = new DBPlayer();
		player.setId(key);
		player.setName(defaultString(safeValue.name));
		player.setLv(safeValue.lv);
		player.setIntIntMap(toIntMap(player, safeValue.intIntMap));
		player.setIntList(toIntList(player, safeValue.intList));
		player.setIntSet(toIntSet(player, safeValue.intSet));
		player.setIntDBItemMap(toItemMap(player, safeValue.intDBItemMap));
		return player;
	}

	private XHashMap<Integer, Integer> toIntMap(DBPlayer player, Map<Integer, Integer> data) {
		XHashMap<Integer, Integer> map = new XHashMap<>(player);
		if (data != null) {
			map.putAll(data);
		}
		return map;
	}

	private XArrayList<Integer> toIntList(DBPlayer player, List<Integer> data) {
		XArrayList<Integer> list = new XArrayList<>(player);
		if (data != null) {
			list.addAll(data);
		}
		return list;
	}

	private XHashSet<Integer> toIntSet(DBPlayer player, Set<Integer> data) {
		XHashSet<Integer> set = new XHashSet<>(player);
		if (data != null) {
			set.addAll(data);
		}
		return set;
	}

	private XHashMap<Integer, DBItem> toItemMap(DBPlayer player, Map<Integer, ItemJsonValue> data) {
		XHashMap<Integer, DBItem> map = new XHashMap<>(player);
		if (data != null) {
			data.forEach((key, value) -> {
				DBItem item = fromJsonItem(value);
				item.setParent(map);
				map.put(key, item);
			});
		}
		return map;
	}

	private DBItem fromJsonItem(ItemJsonValue value) {
		ItemJsonValue safeValue = value == null ? new ItemJsonValue() : value;
		DBItem item = new DBItem();
		item.setItemSrl(safeValue.itemSrl);
		item.setItemId(safeValue.itemId);
		item.setItemName(defaultString(safeValue.itemName));
		return item;
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

	private String createBatchGetSql(Map<Long, DBPlayer> map) {
		requireBatchMap(map);
		return "SELECT k, v FROM " + TABLE_NAME + " WHERE k IN (" + createPlaceholders(map.size()) + ")";
	}

	private String createBatchRemoveSql(Map<Long, DBPlayer> map) {
		requireBatchMap(map);
		return "DELETE FROM " + TABLE_NAME + " WHERE k IN (" + createPlaceholders(map.size()) + ")";
	}

	private String createPlaceholders(int size) {
		StringBuilder builder = new StringBuilder(size * 3 - 1);
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append('?');
		}
		return builder.toString();
	}

	private String defaultString(String value) {
		return value == null ? "" : value;
	}

	private <T> T requireNotNull(T value, String fieldName) {
		if (value == null) {
			throw new IllegalArgumentException(fieldName + " 不能为空");
		}
		return value;
	}

	private static class PlayerJsonValue {
		public String name = "";
		public int lv;
		public Map<Integer, Integer> intIntMap = new LinkedHashMap<>();
		public List<Integer> intList = new ArrayList<>();
		public Set<Integer> intSet = new java.util.LinkedHashSet<>();
		public Map<Integer, ItemJsonValue> intDBItemMap = new LinkedHashMap<>();
	}

	private static class ItemJsonValue {
		public long itemSrl;
		public int itemId;
		public String itemName = "";
	}
}
