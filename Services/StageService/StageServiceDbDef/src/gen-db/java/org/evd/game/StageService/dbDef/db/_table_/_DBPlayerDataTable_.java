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
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class _DBPlayerDataTable_ extends TTable<Long, DBPlayerData> {
    private static final String TABLE_NAME = "db_player_data";
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS db_player_data (
                k BIGINT NOT NULL PRIMARY KEY,
                v MEDIUMBLOB NOT NULL
            ) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI
            """;
    private static final String GET_SQL = "SELECT k, v FROM " + TABLE_NAME + " WHERE k = ?";
    private static final String SAVE_SQL = "REPLACE INTO " + TABLE_NAME + " (k, v) VALUES (?, ?)";
    private static final String REMOVE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE k = ?";

    private static final Schema<DBPlayerData> SCHEMA = RuntimeSchema.getSchema(DBPlayerData.class);

    private _DBPlayerDataTable_() {
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }

    @Override
    protected DBPlayerData newValue() {
        return new DBPlayerData();
    }

    @Override
    public DBReq createCreateTableDBReq() {
        return createReq(DbOpType.CREATE_TABLE, CREATE_TABLE_SQL, new ArrayList<>());
    }

    @Override
    public DBReq createGetDBReq(Long key) {
        return createReq(DbOpType.GET, GET_SQL, List.of(createKeyField(key)));
    }

    @Override
    public DBReq createSaveDBReq(DBPlayerData value) {
        Long key = getPrimaryKey(value);
        return createReq(DbOpType.SAVE, SAVE_SQL, List.of(toSaveField(key, value)));
    }

    @Override
    public DBReq createRemoveDBReq(Long key) {
        return createReq(DbOpType.REMOVE, REMOVE_SQL, List.of(createKeyField(key)));
    }

    @Override
    public DBReq createBatchGetDBReq(Map<Long, DBPlayerData> map) {
        return createReq(DbOpType.BATCH_GET, createBatchGetSql(map), toKeyFieldList(map));
    }

    @Override
    public DBReq createBatchSaveDBReq(Map<Long, DBPlayerData> map) {
        requireBatchMap(map);
        List<DbTableField> tableFieldList = new ArrayList<>(map.size());
        for (Map.Entry<Long, DBPlayerData> entry : map.entrySet()) {
            tableFieldList.add(toSaveField(entry.getKey(), entry.getValue()));
        }
        return createReq(DbOpType.BATCH_SAVE, SAVE_SQL, tableFieldList);
    }

    @Override
    public DBReq createBatchRemoveDBReq(Map<Long, DBPlayerData> map) {
        return createReq(DbOpType.BATCH_REMOVE, createBatchRemoveSql(map), toKeyFieldList(map));
    }

    @Override
    public DBPlayerData parseGetDBRsp(DBRsp rsp) {
        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return null;
        }
        return parseRow(tableFieldList.get(0));
    }

    @Override
    public Map<Long, DBPlayerData> parseBatchGetDBRsp(DBRsp rsp) {
        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
        Map<Long, DBPlayerData> result = new LinkedHashMap<>();
        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return result;
        }
        for (DbTableField tableField : tableFieldList) {
            Long key = parseRowKey(tableField);
            DBPlayerData value = parseRow(tableField);
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

    private DbTableField createKeyField(Long key) {
        Objects.requireNonNull(key, "key 不能为空");
        return new DbTableField(List.of(new DbValue(key)));
    }

    private List<DbTableField> toKeyFieldList(Map<Long, DBPlayerData> map) {
        requireBatchMap(map);
        List<DbTableField> tableFieldList = new ArrayList<>(map.size());
        for (Long key : map.keySet()) {
            tableFieldList.add(createKeyField(key));
        }
        return tableFieldList;
    }

    private Long getPrimaryKey(DBPlayerData value) {
        return value.getId();
    }

    private DbTableField toSaveField(Long key, DBPlayerData value) {
        Objects.requireNonNull(value, "value 不能为空");
        List<DbValue> valueList = new ArrayList<>(2);
        valueList.add(new DbValue(key));
        valueList.add(new DbValue(serializeBean(value)));
        return new DbTableField(valueList);
    }

    private Long parseRowKey(DbTableField tableField) {
        List<DbValue> valueList = tableField.getValueList();
        if (valueList == null || valueList.isEmpty()) {
            return null;
        }
        return ((Number) valueList.get(0).getV()).longValue();
    }

    private DBPlayerData parseRow(DbTableField tableField) {
        List<DbValue> valueList = tableField.getValueList();
        if (valueList == null || valueList.size() < 2) {
            return null;
        }
        Long key = ((Number) valueList.get(0).getV()).longValue();
        byte[] payload = (byte[]) valueList.get(1).getV();
        DBPlayerData value = deserializeBean(payload);
        value.setId(key);
        value.dirty = false;
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

    private void requireBatchMap(Map<Long, DBPlayerData> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("batch map 不能为空");
        }
    }

    private String createBatchGetSql(Map<Long, DBPlayerData> map) {
        requireBatchMap(map);
        return "SELECT k, v FROM " + TABLE_NAME + " WHERE k IN (" + createPlaceholders(map.size()) + ")";
    }

    private String createBatchRemoveSql(Map<Long, DBPlayerData> map) {
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

    private byte[] serializeBean(DBPlayerData value) {
        LinkedBuffer buffer = LinkedBuffer.allocate();
        try {
            return ProtostuffIOUtil.toByteArray(value, SCHEMA, buffer);
        } finally {
            buffer.clear();
        }
    }

    private DBPlayerData deserializeBean(byte[] bytes) {
        DBPlayerData value = new DBPlayerData();
        if (bytes == null || bytes.length == 0) {
            value.dirty = false;
            return value;
        }
        ProtostuffIOUtil.mergeFrom(bytes, value, SCHEMA);
        value.dirty = false;
        return value;
    }
}
