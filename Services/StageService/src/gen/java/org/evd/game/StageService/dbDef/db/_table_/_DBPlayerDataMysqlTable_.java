package org.evd.game.StageService.dbDef.db._table_;

import org.evd.game.StageService.dbDef.db.bean.*;
import org.evd.game.base.DirtyObject;
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
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class _DBPlayerDataMysqlTable_ extends TTable<Integer, DBPlayerDataMysql> {
    private static final String TABLE_NAME = "db_player_data_mysql";
    private static final String SELECT_COLUMNS = "id, name, lv, int_int_map, int_list, int_set, int_db_item_map, obj1, bytes";
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS db_player_data_mysql (
                id INT NOT NULL PRIMARY KEY,
                name VARCHAR(128) NOT NULL,
                lv INT NOT NULL,
                int_int_map MEDIUMTEXT NOT NULL,
                int_list MEDIUMTEXT NOT NULL,
                int_set MEDIUMTEXT NOT NULL,
                int_db_item_map MEDIUMTEXT NOT NULL,
                obj1 MEDIUMTEXT NOT NULL,
                bytes MEDIUMTEXT NOT NULL
            ) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI
            """;
    private static final String GET_SQL = "SELECT " + SELECT_COLUMNS + " FROM " + TABLE_NAME + " WHERE id = ?";
    private static final String SAVE_SQL = "REPLACE INTO " + TABLE_NAME + " (id, name, lv, int_int_map, int_list, int_set, int_db_item_map, obj1, bytes) VALUES (?,?,?,?,?,?,?,?,?)";
    private static final String REMOVE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

    private _DBPlayerDataMysqlTable_() {
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }

    @Override
    protected DBPlayerDataMysql newValue() {
        return new DBPlayerDataMysql();
    }

    @Override
    public DBReq createCreateTableDBReq() {
        return createReq(DbOpType.CREATE_TABLE, CREATE_TABLE_SQL, new ArrayList<>());
    }

    @Override
    public DBReq createGetDBReq(Integer key) {
        return createReq(DbOpType.GET, GET_SQL, List.of(createKeyField(key)));
    }

    @Override
    public DBReq createSaveDBReq(DBPlayerDataMysql value) {
        return createReq(DbOpType.SAVE, SAVE_SQL, List.of(toTableField(value)));
    }

    @Override
    public DBReq createRemoveDBReq(Integer key) {
        return createReq(DbOpType.REMOVE, REMOVE_SQL, List.of(createKeyField(key)));
    }

    @Override
    public DBReq createBatchGetDBReq(Map<Integer, DBPlayerDataMysql> map) {
        return createReq(DbOpType.BATCH_GET, createBatchGetSql(map), toKeyFieldList(map));
    }

    @Override
    public DBReq createBatchSaveDBReq(Map<Integer, DBPlayerDataMysql> map) {
        requireBatchMap(map);
        List<DbTableField> tableFieldList = new ArrayList<>(map.size());
        for (DBPlayerDataMysql value : map.values()) {
            tableFieldList.add(toTableField(value));
        }
        return createReq(DbOpType.BATCH_SAVE, SAVE_SQL, tableFieldList);
    }

    @Override
    public DBReq createBatchRemoveDBReq(Map<Integer, DBPlayerDataMysql> map) {
        return createReq(DbOpType.BATCH_REMOVE, createBatchRemoveSql(map), toKeyFieldList(map));
    }

    @Override
    public DBPlayerDataMysql parseGetDBRsp(DBRsp rsp) {
        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return null;
        }
        return parseRow(tableFieldList.get(0));
    }

    @Override
    public Map<Integer, DBPlayerDataMysql> parseBatchGetDBRsp(DBRsp rsp) {
        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
        Map<Integer, DBPlayerDataMysql> result = new LinkedHashMap<>();
        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return result;
        }
        for (DbTableField tableField : tableFieldList) {
            Integer key = parseRowKey(tableField);
            DBPlayerDataMysql value = parseRow(tableField);
            if (key == null) {
                if (value != null) {
                    result.put(getPrimaryKey(value), value);
                }
                continue;
            }
            result.put(key, value);
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

    private DbTableField createKeyField(Integer key) {
        Objects.requireNonNull(key, "key 不能为空");
        return new DbTableField(List.of(new DbValue(key)));
    }

    private List<DbTableField> toKeyFieldList(Map<Integer, DBPlayerDataMysql> map) {
        requireBatchMap(map);
        List<DbTableField> tableFieldList = new ArrayList<>(map.size());
        for (Integer key : map.keySet()) {
            tableFieldList.add(createKeyField(key));
        }
        return tableFieldList;
    }

    private Integer getPrimaryKey(DBPlayerDataMysql value) {
        return value.getId();
    }

    private DbTableField toTableField(DBPlayerDataMysql value) {
        Objects.requireNonNull(value, "value 不能为空");
        List<DbValue> valueList = new ArrayList<>(9);
        valueList.add(new DbValue(value.getId()));
        valueList.add(new DbValue(value.getName()));
        valueList.add(new DbValue(value.getLv()));
        valueList.add(new DbValue(serializeIntIntMap(value.getIntIntMap())));
        valueList.add(new DbValue(serializeIntList(value.getIntList())));
        valueList.add(new DbValue(serializeIntSet(value.getIntSet())));
        valueList.add(new DbValue(serializeIntDBItemMap(value.getIntDBItemMap())));
        valueList.add(new DbValue(serializeObj1(value.getObj1())));
        valueList.add(new DbValue(serializeBytes(value.getBytes())));
        return new DbTableField(valueList);
    }

    private Integer parseRowKey(DbTableField tableField) {
        List<DbValue> valueList = tableField.getValueList();
        if (valueList == null || valueList.size() <= 0) {
            return null;
        }
        return ((Number) valueList.get(0).getV()).intValue();
    }

    private DBPlayerDataMysql parseRow(DbTableField tableField) {
        List<DbValue> valueList = tableField.getValueList();
        if (valueList == null || valueList.size() < 9) {
            return null;
        }
        DBPlayerDataMysql value = new DBPlayerDataMysql();
        value.setId(((Number) valueList.get(0).getV()).intValue());
        value.setName((String) valueList.get(1).getV());
        value.setLv(((Number) valueList.get(2).getV()).intValue());
        value.setIntIntMap(deserializeIntIntMap((String) valueList.get(3).getV(), value));
        value.setIntList(deserializeIntList((String) valueList.get(4).getV(), value));
        value.setIntSet(deserializeIntSet((String) valueList.get(5).getV(), value));
        value.setIntDBItemMap(deserializeIntDBItemMap((String) valueList.get(6).getV(), value));
        value.setObj1(deserializeObj1((String) valueList.get(7).getV(), value));
        value.setBytes(deserializeBytes((String) valueList.get(8).getV(), value));
        value.dirty = false;
        return value;
    }

    private String serializeIntIntMap(java.util.Map<Integer, Integer> value) {
        return JSON.toJSONString(value);
    }

    private XHashMap<Integer, Integer> deserializeIntIntMap(String text, DirtyObject owner) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return new XHashMap<>(owner);
        }
        java.util.Map<Integer, Integer> data = JSON.parseObject(text, new TypeReference<java.util.Map<Integer, Integer>>() {
        });
        XHashMap<Integer, Integer> result = new XHashMap<>(owner);
        if (data != null) {
            data.forEach((key, value) -> {
                result.put(key, value);
            });
        }
        return result;
    }

    private String serializeIntList(java.util.List<Integer> value) {
        return JSON.toJSONString(value);
    }

    private XArrayList<Integer> deserializeIntList(String text, DirtyObject owner) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return new XArrayList<>(owner);
        }
        java.util.List<Integer> data = JSON.parseObject(text, new TypeReference<java.util.List<Integer>>() {
        });
        XArrayList<Integer> result = new XArrayList<>(owner);
        if (data != null) {
            result.addAll(data);
        }
        return result;
    }

    private String serializeIntSet(java.util.Set<Integer> value) {
        return JSON.toJSONString(value);
    }

    private XHashSet<Integer> deserializeIntSet(String text, DirtyObject owner) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return new XHashSet<>(owner);
        }
        java.util.Set<Integer> data = JSON.parseObject(text, new TypeReference<java.util.Set<Integer>>() {
        });
        XHashSet<Integer> result = new XHashSet<>(owner);
        if (data != null) {
            result.addAll(data);
        }
        return result;
    }

    private String serializeIntDBItemMap(java.util.Map<Integer, DBItemDataMysql> value) {
        return JSON.toJSONString(value);
    }

    private XHashMap<Integer, DBItemDataMysql> deserializeIntDBItemMap(String text, DirtyObject owner) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return new XHashMap<>(owner);
        }
        java.util.Map<Integer, DBItemDataMysql> data = JSON.parseObject(text, new TypeReference<java.util.Map<Integer, DBItemDataMysql>>() {
        });
        XHashMap<Integer, DBItemDataMysql> result = new XHashMap<>(owner);
        if (data != null) {
            data.forEach((key, value) -> {
                if (value != null) {
                    value.setParent(result);
                }
                result.put(key, value);
            });
        }
        return result;
    }

    private String serializeObj1(DBItemDataMysql value) {
        return JSON.toJSONString(value);
    }

    private DBItemDataMysql deserializeObj1(String text, DirtyObject owner) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return null;
        }
        DBItemDataMysql result = JSON.parseObject(text, DBItemDataMysql.class);
        if (result != null) {
            result.setParent(owner);
        }
        return result;
    }

    private String serializeBytes(byte[] value) {
        return JSON.toJSONString(value);
    }

    private byte[] deserializeBytes(String text, DirtyObject owner) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return null;
        }
        return JSON.parseObject(text, new TypeReference<byte[]>() {
        });
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

    private void requireBatchMap(Map<Integer, DBPlayerDataMysql> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("batch map 不能为空");
        }
    }

    private String createBatchGetSql(Map<Integer, DBPlayerDataMysql> map) {
        requireBatchMap(map);
        return "SELECT " + SELECT_COLUMNS + " FROM " + TABLE_NAME + " WHERE id IN (" + createPlaceholders(map.size()) + ")";
    }

    private String createBatchRemoveSql(Map<Integer, DBPlayerDataMysql> map) {
        requireBatchMap(map);
        return "DELETE FROM " + TABLE_NAME + " WHERE id IN (" + createPlaceholders(map.size()) + ")";
    }

    private String createPlaceholders(int size) {
        StringBuilder builder = new StringBuilder(Math.max(0, size * 3 - 1));
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("?");
        }
        return builder.toString();
    }
}
