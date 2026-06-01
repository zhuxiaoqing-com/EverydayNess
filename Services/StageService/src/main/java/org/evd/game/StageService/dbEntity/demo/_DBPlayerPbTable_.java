package org.evd.game.StageService.dbEntity.demo;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import org.evd.game.Db.collection.XArrayList;
import org.evd.game.Db.collection.XHashMap;
import org.evd.game.Db.collection.XHashSet;
import org.evd.game.Db.serialize.DBReq;
import org.evd.game.Db.serialize.DBRsp;
import org.evd.game.Db.serialize.DbOpType;
import org.evd.game.Db.serialize.DbTableField;
import org.evd.game.Db.serialize.DbValue;
import org.evd.game.Db.serialize.MysqlReq;
import org.evd.game.Db.serialize.MysqlRsp;
import org.evd.game.Db.table.TTable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class _DBPlayerPbTable_ extends TTable<Long, DBPlayer> {
	private static final String TABLE_NAME = "db_player_pb";
	private static final String CREATE_TABLE_SQL = """
			CREATE TABLE IF NOT EXISTS db_player_pb (
			    k BIGINT NOT NULL PRIMARY KEY,
			    v MEDIUMBLOB NOT NULL
			) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI
			""";
	private static final String GET_SQL = "SELECT k, v FROM " + TABLE_NAME + " WHERE k = ?";
	private static final String SAVE_SQL = "REPLACE INTO " + TABLE_NAME + " (k, v) VALUES (?, ?)";
	private static final String REMOVE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE k = ?";

	private static final int FIELD_NAME = 1;
	private static final int FIELD_LV = 2;
	private static final int FIELD_INT_INT_MAP = 3;
	private static final int FIELD_INT_LIST = 4;
	private static final int FIELD_INT_SET = 5;
	private static final int FIELD_ITEM_MAP = 6;

	private static final int FIELD_ENTRY_KEY = 1;
	private static final int FIELD_ENTRY_VALUE = 2;

	private static final int FIELD_ITEM_SRL = 1;
	private static final int FIELD_ITEM_ID = 2;
	private static final int FIELD_ITEM_NAME = 3;

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
		valueList.add(new DbValue(encodePlayer(player)));
		return new DbTableField(valueList);
	}

	private DBPlayer parseRow(DbTableField tableField) {
		List<DbValue> valueList = tableField.getValueList();
		if (valueList == null || valueList.size() != 2) {
			throw new IllegalArgumentException("DBPlayerPb row column size error: " + (valueList == null ? 0 : valueList.size()));
		}
		long key = ((Number) valueList.get(0).getV()).longValue();
		DBPlayer player = decodePlayer(key, (byte[]) valueList.get(1).getV());
		player.dirty = false;
		return player;
	}

	private byte[] encodePlayer(DBPlayer player) {
		try {
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			CodedOutputStream output = CodedOutputStream.newInstance(byteArrayOutputStream);
			output.writeString(FIELD_NAME, requireNotNull(player.getName(), "name"));
			output.writeInt32(FIELD_LV, player.getLv());
			for (Map.Entry<Integer, Integer> entry : requireNotNull(player.getIntIntMap(), "intIntMap").entrySet()) {
				writeBytesField(output, FIELD_INT_INT_MAP, encodeIntIntEntry(entry.getKey(), entry.getValue()));
			}
			for (Integer value : requireNotNull(player.getIntList(), "intList")) {
				output.writeInt32(FIELD_INT_LIST, value);
			}
			for (Integer value : requireNotNull(player.getIntSet(), "intSet")) {
				output.writeInt32(FIELD_INT_SET, value);
			}
			for (Map.Entry<Integer, DBItem> entry : requireNotNull(player.getIntDBItemMap(), "intDBItemMap").entrySet()) {
				writeBytesField(output, FIELD_ITEM_MAP, encodeItemEntry(entry.getKey(), entry.getValue()));
			}
			output.flush();
			return byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("encode player pb error", e);
		}
	}

	private byte[] encodeIntIntEntry(Integer key, Integer value) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		CodedOutputStream output = CodedOutputStream.newInstance(byteArrayOutputStream);
		output.writeInt32(FIELD_ENTRY_KEY, key);
		output.writeInt32(FIELD_ENTRY_VALUE, value);
		output.flush();
		return byteArrayOutputStream.toByteArray();
	}

	private byte[] encodeItemEntry(Integer key, DBItem item) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		CodedOutputStream output = CodedOutputStream.newInstance(byteArrayOutputStream);
		output.writeInt32(FIELD_ENTRY_KEY, key);
		writeBytesField(output, FIELD_ENTRY_VALUE, encodeItem(item));
		output.flush();
		return byteArrayOutputStream.toByteArray();
	}

	private byte[] encodeItem(DBItem item) throws IOException {
		Objects.requireNonNull(item, "item 不能为空");
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		CodedOutputStream output = CodedOutputStream.newInstance(byteArrayOutputStream);
		output.writeInt64(FIELD_ITEM_SRL, item.getItemSrl());
		output.writeInt32(FIELD_ITEM_ID, item.getItemId());
		output.writeString(FIELD_ITEM_NAME, requireNotNull(item.getItemName(), "itemName"));
		output.flush();
		return byteArrayOutputStream.toByteArray();
	}

	private DBPlayer decodePlayer(long key, byte[] bytes) {
		try {
			DBPlayer player = new DBPlayer();
			player.setId(key);
			XHashMap<Integer, Integer> intIntMap = new XHashMap<>(player);
			XArrayList<Integer> intList = new XArrayList<>(player);
			XHashSet<Integer> intSet = new XHashSet<>(player);
			XHashMap<Integer, DBItem> intDBItemMap = new XHashMap<>(player);

			CodedInputStream input = CodedInputStream.newInstance(bytes);
			while (!input.isAtEnd()) {
				int tag = input.readTag();
				if (tag == 0) {
					break;
				}
				switch (WireFormat.getTagFieldNumber(tag)) {
					case FIELD_NAME -> player.setName(input.readString());
					case FIELD_LV -> player.setLv(input.readInt32());
					case FIELD_INT_INT_MAP -> {
						IntIntEntry entry = decodeIntIntEntry(input.readByteArray());
						intIntMap.put(entry.key, entry.value);
					}
					case FIELD_INT_LIST -> intList.add(input.readInt32());
					case FIELD_INT_SET -> intSet.add(input.readInt32());
					case FIELD_ITEM_MAP -> {
						ItemEntry entry = decodeItemEntry(input.readByteArray());
						if (entry.item != null) {
							entry.item.setParent(intDBItemMap);
						}
						intDBItemMap.put(entry.key, entry.item);
					}
					default -> input.skipField(tag);
				}
			}

			player.setName(defaultString(player.getName()));
			player.setIntIntMap(intIntMap);
			player.setIntList(intList);
			player.setIntSet(intSet);
			player.setIntDBItemMap(intDBItemMap);
			return player;
		} catch (IOException e) {
			throw new IllegalStateException("decode player pb error", e);
		}
	}

	private IntIntEntry decodeIntIntEntry(byte[] bytes) throws IOException {
		CodedInputStream input = CodedInputStream.newInstance(bytes);
		IntIntEntry entry = new IntIntEntry();
		while (!input.isAtEnd()) {
			int tag = input.readTag();
			if (tag == 0) {
				break;
			}
			switch (WireFormat.getTagFieldNumber(tag)) {
				case FIELD_ENTRY_KEY -> entry.key = input.readInt32();
				case FIELD_ENTRY_VALUE -> entry.value = input.readInt32();
				default -> input.skipField(tag);
			}
		}
		return entry;
	}

	private ItemEntry decodeItemEntry(byte[] bytes) throws IOException {
		CodedInputStream input = CodedInputStream.newInstance(bytes);
		ItemEntry entry = new ItemEntry();
		while (!input.isAtEnd()) {
			int tag = input.readTag();
			if (tag == 0) {
				break;
			}
			switch (WireFormat.getTagFieldNumber(tag)) {
				case FIELD_ENTRY_KEY -> entry.key = input.readInt32();
				case FIELD_ENTRY_VALUE -> entry.item = decodeItem(input.readByteArray());
				default -> input.skipField(tag);
			}
		}
		return entry;
	}

	private DBItem decodeItem(byte[] bytes) throws IOException {
		CodedInputStream input = CodedInputStream.newInstance(bytes);
		DBItem item = new DBItem();
		while (!input.isAtEnd()) {
			int tag = input.readTag();
			if (tag == 0) {
				break;
			}
			switch (WireFormat.getTagFieldNumber(tag)) {
				case FIELD_ITEM_SRL -> item.setItemSrl(input.readInt64());
				case FIELD_ITEM_ID -> item.setItemId(input.readInt32());
				case FIELD_ITEM_NAME -> item.setItemName(input.readString());
				default -> input.skipField(tag);
			}
		}
		item.setItemName(defaultString(item.getItemName()));
		return item;
	}

	private void writeBytesField(CodedOutputStream output, int fieldNumber, byte[] bytes) throws IOException {
		output.writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
		output.writeUInt32NoTag(bytes.length);
		output.writeRawBytes(bytes);
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

	private static class IntIntEntry {
		private int key;
		private int value;
	}

	private static class ItemEntry {
		private int key;
		private DBItem item;
	}
}
