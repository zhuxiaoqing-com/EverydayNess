package org.evd.game.StageService.dbEntity.demo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 构建 mysql.table形式的数据;
 * byte / Byte	TINYINT	1 字节整数
 * short / Short	SMALLINT	2 字节整数
 * int / Integer	INT	最常用
 * long / Long	BIGINT	角色 ID、时间戳、唯一 ID
 * float / Float	FLOAT	不推荐存重要精度数据
 * double / Double	DOUBLE	比 FLOAT 精度高
 * boolean / Boolean	TINYINT(1)	MySQL 没真正 boolean，本质是 0/1
 * char / Character	CHAR(1)	单字符
 * String	VARCHAR(128) / TEXT / MEDIUMTEXT	看长度和是否索引
 * byte[]	BLOB / MEDIUMBLOB	二进制，比如 protobuf
 * 集合直接json转,MEDIUMTEXT：
 */

/**
 * 构建 mysql.table 形式的数据。
 */
public class _DBPlayerTable_ extends TTable<Long, DBPlayer> {
    private static final String TABLE_NAME = "db_player";
    private static final String SELECT_COLUMNS = "id, name, lv, int_int_map, int_list, int_set, int_db_item_map";
    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS db_player (
                id BIGINT NOT NULL PRIMARY KEY,
                name VARCHAR(128) NOT NULL,
                lv INT NOT NULL,
                int_int_map MEDIUMTEXT NOT NULL,
                int_list MEDIUMTEXT NOT NULL,
                int_set MEDIUMTEXT NOT NULL,
                int_db_item_map MEDIUMTEXT NOT NULL
            ) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI
            """;
    private static final String GET_SQL = "SELECT " + SELECT_COLUMNS + " FROM " + TABLE_NAME + " WHERE id = ?";
    private static final String SAVE_SQL = "REPLACE INTO " + TABLE_NAME
            + " (id, name, lv, int_int_map, int_list, int_set, int_db_item_map) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String REMOVE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

    private _DBPlayerTable_() {
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }

    @Override
    protected DBPlayer newValue() {
        return new DBPlayer();
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
    public DBReq createSaveDBReq(DBPlayer player) {
        return createReq(DbOpType.SAVE, SAVE_SQL, List.of(toTableField(player)));
    }

    @Override
    public DBReq createRemoveDBReq(Long key) {
        return createReq(DbOpType.REMOVE, REMOVE_SQL, List.of(createKeyField(key)));
    }

    @Override
    public DBReq createBatchGetDBReq(Map<Long, DBPlayer> map) {
        return createReq(DbOpType.BATCH_GET, createBatchGetSql(map), toKeyFieldList(map));
    }

    @Override
    public DBReq createBatchSaveDBReq(Map<Long, DBPlayer> map) {
        requireBatchMap(map);
        List<DbTableField> tableFieldList = new ArrayList<>(map.size());
        for (DBPlayer player : map.values()) {
            tableFieldList.add(toTableField(player));
        }
        return createReq(DbOpType.BATCH_SAVE, SAVE_SQL, tableFieldList);
    }

    @Override
    public DBReq createBatchRemoveDBReq(Map<Long, DBPlayer> map) {
        return createReq(DbOpType.BATCH_REMOVE, createBatchRemoveSql(map), toKeyFieldList(map));
    }

    @Override
    public DBPlayer parseGetDBRsp(DBRsp rsp) {
        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);
        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return null;
        }
        return parseRow(tableFieldList.get(0));
    }

    @Override
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

    private DbTableField toTableField(DBPlayer player) {
        Objects.requireNonNull(player, "player 不能为空");
        List<DbValue> valueList = new ArrayList<>(7);
        valueList.add(new DbValue(player.getId()));
        valueList.add(new DbValue(requireNotNull(player.getName(), "name")));
        valueList.add(new DbValue(player.getLv()));
        valueList.add(new DbValue(JSON.toJSONString(requireNotNull(player.getIntIntMap(), "intIntMap"))));
        valueList.add(new DbValue(JSON.toJSONString(requireNotNull(player.getIntList(), "intList"))));
        valueList.add(new DbValue(JSON.toJSONString(requireNotNull(player.getIntSet(), "intSet"))));
        valueList.add(new DbValue(JSON.toJSONString(requireNotNull(player.getIntDBItemMap(), "intDBItemMap"))));
        return new DbTableField(valueList);
    }

    private DBPlayer parseRow(DbTableField tableField) {
        List<DbValue> valueList = tableField.getValueList();
        if (valueList == null || valueList.size() != 7) {
            throw new IllegalArgumentException("DBPlayer row column size error: " + (valueList == null ? 0 : valueList.size()));
        }

        DBPlayer player = new DBPlayer();
        player.setId(((Number) valueList.get(0).getV()).longValue());
        player.setName((String) valueList.get(1).getV());
        player.setLv(((Number) valueList.get(2).getV()).intValue());
        player.setIntIntMap(toIntMap(player, JSON.parseObject((String) valueList.get(3).getV(),
                new TypeReference<Map<Integer, Integer>>() {
                })));
        player.setIntList(toXArrayList(player, JSON.parseObject((String) valueList.get(4).getV(),
                new TypeReference<List<Integer>>() {
                })));
        player.setIntSet(toXHashSet(player, JSON.parseObject((String) valueList.get(5).getV(),
                new TypeReference<Set<Integer>>() {
                })));
        player.setIntDBItemMap(toItemMap(player, JSON.parseObject((String) valueList.get(6).getV(),
                new TypeReference<Map<Integer, DBItem>>() {
                })));
        player.dirty = false;
        return player;
    }

    private XHashMap<Integer, Integer> toIntMap(DBPlayer player, Map<Integer, Integer> data) {
        XHashMap<Integer, Integer> map = new XHashMap<>(player);
        if (data != null) {
            map.putAll(data);
        }
        return map;
    }

    private XHashMap<Integer, DBItem> toItemMap(DBPlayer player, Map<Integer, DBItem> data) {
        XHashMap<Integer, DBItem> map = new XHashMap<>(player);
        if (data != null) {
            data.forEach((key, value) -> {
                if (value != null) {
                    value.setParent(map);
                }
                map.put(key, value);
            });
        }
        return map;
    }

    private XArrayList<Integer> toXArrayList(DBPlayer player, List<Integer> data) {
        XArrayList<Integer> list = new XArrayList<>(player);
        if (data != null) {
            list.addAll(data);
        }
        return list;
    }

    private XHashSet<Integer> toXHashSet(DBPlayer player, Set<Integer> data) {
        XHashSet<Integer> set = new XHashSet<>(player);
        if (data != null) {
            set.addAll(data);
        }
        return set;
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
        return "SELECT " + SELECT_COLUMNS + " FROM " + TABLE_NAME + " WHERE id IN (" + createPlaceholders(map.size()) + ")";
    }

    private String createBatchRemoveSql(Map<Long, DBPlayer> map) {
        requireBatchMap(map);
        return "DELETE FROM " + TABLE_NAME + " WHERE id IN (" + createPlaceholders(map.size()) + ")";
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

    private <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
