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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class _DBPlayerDataJSONTable_ extends TTable<String, DBPlayerDataJSON> {
    private static final String TABLE_NAME = "db_player_data_json";
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS db_player_data_json (
                k VARCHAR(128) NOT NULL PRIMARY KEY,
                v MEDIUMTEXT NOT NULL
            ) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI
            """;
    private static final String GET_SQL = "SELECT k, v FROM " + TABLE_NAME + " WHERE k = ?";
    private static final String SAVE_SQL = "REPLACE INTO " + TABLE_NAME + " (k, v) VALUES (?, ?)";
    private static final String REMOVE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE k = ?";

    private _DBPlayerDataJSONTable_() {
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }

    @Override
    protected DBPlayerDataJSON newValue() {
        return new DBPlayerDataJSON();
    }

    @Override
    public DBReq createCreateTableDBReq() {
        return createReq(DbOpType.CREATE_TABLE, CREATE_TABLE_SQL, new ArrayList<>());
    }

    @Override
    public DBReq createGetDBReq(String key) {
        return createReq(DbOpType.GET, GET_SQL, List.of(createKeyField(key)));
    }

    @Override
    public DBReq createSaveDBReq(DBPlayerDataJSON value) {
        String key = getPrimaryKey(value);
        return createReq(DbOpType.SAVE, SAVE_SQL, List.of(toSaveField(key, value)));
    }

    @Override
    public DBReq createRemoveDBReq(String key) {
        return createReq(DbOpType.REMOVE, REMOVE_SQL, List.of(createKeyField(key)));
    }

    @Override
    public DBReq createBatchGetDBReq(Map<String, DBPlayerDataJSON> map) {
        return createReq(DbOpType.BATCH_GET, createBatchGetSql(map), toKeyFieldList(map));
    }

    @Override
    public DBReq createBatchSaveDBReq(Map<String, DBPlayerDataJSON> map) {
        requireBatchMap(map);
        List<DbTableField> tableFieldList = new ArrayList<>(map.size());
        for (Map.Entry<String, DBPlayerDataJSON> entry : map.entrySet()) {
            tableFieldList.add(toSaveField(entry.getKey(), entry.getValue()));
        }
        return createReq(DbOpType.BATCH_SAVE, SAVE_SQL, tableFieldList);
    }

    @Override
    public DBReq createBatchRemoveDBReq(Map<String, DBPlayerDataJSON> map) {
        return createReq(DbOpType.BATCH_REMOVE, createBatchRemoveSql(map), toKeyFieldList(map));
    }

    @Override
    public DBPlayerDataJSON parseGetDBRsp(DBRsp rsp) {
        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return null;
        }
        return parseRow(tableFieldList.get(0));
    }

    @Override
    public Map<String, DBPlayerDataJSON> parseBatchGetDBRsp(DBRsp rsp) {
        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
        Map<String, DBPlayerDataJSON> result = new LinkedHashMap<>();
        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return result;
        }
        for (DbTableField tableField : tableFieldList) {
            String key = parseRowKey(tableField);
            DBPlayerDataJSON value = parseRow(tableField);
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

    private DbTableField createKeyField(String key) {
        Objects.requireNonNull(key, "key 不能为空");
        return new DbTableField(List.of(new DbValue(key)));
    }

    private List<DbTableField> toKeyFieldList(Map<String, DBPlayerDataJSON> map) {
        requireBatchMap(map);
        List<DbTableField> tableFieldList = new ArrayList<>(map.size());
        for (String key : map.keySet()) {
            tableFieldList.add(createKeyField(key));
        }
        return tableFieldList;
    }

    private String getPrimaryKey(DBPlayerDataJSON value) {
        return value.getId();
    }

    private DbTableField toSaveField(String key, DBPlayerDataJSON value) {
        Objects.requireNonNull(value, "value 不能为空");
        List<DbValue> valueList = new ArrayList<>(2);
        valueList.add(new DbValue(key));
        valueList.add(new DbValue(encodeJsonPayload(value)));
        return new DbTableField(valueList);
    }

    private String parseRowKey(DbTableField tableField) {
        List<DbValue> valueList = tableField.getValueList();
        if (valueList == null || valueList.isEmpty()) {
            return null;
        }
        return (String) valueList.get(0).getV();
    }

    private DBPlayerDataJSON parseRow(DbTableField tableField) {
        List<DbValue> valueList = tableField.getValueList();
        if (valueList == null || valueList.size() < 2) {
            return null;
        }
        String key = (String) valueList.get(0).getV();
        String payload = (String) valueList.get(1).getV();
        DBPlayerDataJSON value = decodeJsonPayload(payload);
        value.setId(key);
        value.dirty = false;
        return value;
    }

    private String encodeJsonPayload(DBPlayerDataJSON value) {
        return JSON.toJSONString(value);
    }

    private DBPlayerDataJSON decodeJsonPayload(String payload) {
        if (payload == null || payload.isEmpty()) {
            return new DBPlayerDataJSON();
        }
        DBPlayerDataJSON value = JSON.parseObject(payload, DBPlayerDataJSON.class);
        if (value == null) {
            return new DBPlayerDataJSON();
        }
        return value;
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

    private void requireBatchMap(Map<String, DBPlayerDataJSON> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("batch map 不能为空");
        }
    }

    private String createBatchGetSql(Map<String, DBPlayerDataJSON> map) {
        requireBatchMap(map);
        return "SELECT k, v FROM " + TABLE_NAME + " WHERE k IN (" + createPlaceholders(map.size()) + ")";
    }

    private String createBatchRemoveSql(Map<String, DBPlayerDataJSON> map) {
        requireBatchMap(map);
        return "DELETE FROM " + TABLE_NAME + " WHERE k IN (" + createPlaceholders(map.size()) + ")";
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
