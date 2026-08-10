package org.evd.game.DBService.storage.mysql;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.evd.game.DBService.DBService;
import org.evd.game.DBService.entity.DBCache;
import org.evd.game.DBService.entity.MysqlTRecord;
import org.evd.game.DBService.entity.TRecord;
import org.evd.game.DBService.entity.TableCache;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.Db.serialize.DbTableField;
import org.evd.game.runtime.Db.serialize.DbValue;
import org.evd.game.runtime.Db.serialize.MysqlReq;
import org.evd.game.runtime.Db.serialize.MysqlRsp;
import org.evd.game.runtime.Db.serialize.MysqlTableMeta;
import org.evd.game.runtime.config.DbStorageConfig;
import org.evd.game.runtime.support.exception.SysException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class StorageMysql implements StorageEngine {
    private static final Logger log = LoggerFactory.getLogger(StorageMysql.class);

    private final int batchPerCount;
    private final int costMsWarn;
    private final int batchCostMsWarn;
    private final Duration operationTimeout;
    private final long dbOperationTimeoutMillis;
    private final DBService dbService;
    private final LoggerMysql logger;
    /** 建表后把表结构注册下来，后续 CRUD 在这里统一拼 SQL。 */
    private final Map<String, TableMeta> tableMetaCache = new HashMap<>();

    public StorageMysql(DBService dbService, LoggerMysql logger, DbStorageConfig storageConfig) {
        this.dbService = dbService;
        this.logger = logger;
        this.batchPerCount = storageConfig.getBatchPerCount();
        this.costMsWarn = storageConfig.getCostMsWarn();
        this.batchCostMsWarn = storageConfig.getBatchCostMsWarn();
        this.operationTimeout = logger.getOperationTimeout();
        this.dbOperationTimeoutMillis = logger.getOperationTimeout().toMillis();
    }

    /**
     * 这里等下单独弄个rpc进行建表，然后返回
     */
    @Override
    public void initTable(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        String createTableSql = mysqlReq.getSql();
        long begin = System.nanoTime();
        Mono<Void> operation;
        if (logger.isAutoCreate()) {
            operation = Mono.usingWhen(
                    logger.openWriteConnection(),
                    connection -> executeWrite(connection, createTableSql, mysqlReq.getTablFieldList()),
                    Connection::close
            );
        } else {
            operation = Mono.usingWhen(
                    logger.openWriteConnection(),
                    connection -> Flux.from(connection.createStatement(
                                            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                                                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")
                                    .bind(0, mysqlReq.getTableName())
                                    .execute())
                            .flatMap(result -> result.map((row, metadata) -> row.get(0, String.class)))
                            .hasElements()
                            .flatMap(exists -> {
                                if (Boolean.TRUE.equals(exists)) {
                                    return Mono.empty();
                }
                return Mono.error(new SysException(
                        "table is not inited, please execute initTable.sql into mysql."));
            }),
                    Connection::close
            );
        }
        await(operation.timeout(operationTimeout)
                .doOnError(e -> log.error("init error, table={}", tableName, e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} init target {} cost: {} ms", tableName, tableName, costMs);
                    }
                }));



        MysqlTableMeta tableMetaData = mysqlReq.getTableMeta();
        if (tableMetaData == null) {
            return;
        }

        TableMeta tableMeta = tableMetaCache.get(tableName);
        if (tableMeta == null) {
            String keyColumnName = tableMetaData.getKeyColumnName();
            List<String> columnNames = tableMetaData.getColumnNames();
            tableMeta = new TableMeta(tableName, keyColumnName, List.copyOf(columnNames));
            tableMetaCache.put(tableName, tableMeta);
            log.info("TableMeta register  {}", tableMeta);
        }
    }

    @Override
    public void replace(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        Object key = mysqlReq.getSingleTableKey();
        String sql = resolveSql(dbReq);
        long begin = System.nanoTime();
        Mono<Void> operation = Mono.usingWhen(
                logger.openWriteConnection(),
                connection -> executeWrite(connection, sql, List.of(mysqlReq.getSingleTableField())),
                Connection::close
        );
        await(operation.timeout(operationTimeout)
                .doOnError(e -> log.error("replace error, table={}, key={}", tableName, key, e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} replace key {} cost: {} ms", tableName, key, costMs);
                    }
                }));
    }

    @Override
    public void replaceBatch(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        List<DbTableField> tableFieldList = mysqlReq.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return;
        }
        String tableName = mysqlReq.getTableName();
        String batchKeys = getBatchKeys(mysqlReq);
        String sql = resolveSql(dbReq);
        long begin = System.nanoTime();
        await(Mono.usingWhen(
                        logger.openWriteConnection(),
                        connection -> {
                            List<DbTableField> tableFieldList1 = mysqlReq.getTablFieldList();
                            if (tableFieldList1.size() <= batchPerCount) {
                                return executeWrite(connection, sql, tableFieldList1);
                            }
                            List<Mono<Void>> operations = new ArrayList<>();
                            for (int start = 0; start < tableFieldList1.size(); start += batchPerCount) {
                                int end = Math.min(start + batchPerCount, tableFieldList1.size());
                                operations.add(executeWrite(connection, sql, tableFieldList1.subList(start, end)));
                            }
                            return Flux.concat(operations).then();
                        },
                        Connection::close
                ).timeout(operationTimeout)
                .doOnError(e -> log.error("replace batch error, table={}, keys={}, num={}",
                        tableName, batchKeys, tableFieldList.size(), e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > batchCostMsWarn) {
                        log.warn("table {} batch replace keys {} num {} cost: {} ms",
                                tableName, batchKeys, tableFieldList.size(), costMs);
                    }
                }));
    }

    @Override
    public void removeBatch(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        List<DbTableField> tableFieldList = mysqlReq.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return;
        }
        String tableName = mysqlReq.getTableName();
        String batchKeys = getBatchKeys(mysqlReq);
        String sql = resolveSql(dbReq);
        long begin = System.nanoTime();
        await(Mono.usingWhen(
                        logger.openWriteConnection(),
                        connection -> {
                            List<DbTableField> tableFieldList1 = mysqlReq.getTablFieldList();
                            Statement statement = connection.createStatement(sql);
                            if (tableFieldList1 != null && !tableFieldList1.isEmpty()) {
                                int paramIndex = 0;
                                for (DbTableField tableField : tableFieldList1) {
                                    statement.bind(paramIndex++, tableField.getTableKey());
                                }
                            }
                            return Flux.from(statement.execute())
                                    .flatMap(Result::getRowsUpdated)
                                    .then();
                        },
                        Connection::close
                ).timeout(operationTimeout)
                .doOnError(e -> log.error("remove batch error, table={}, keys={}, num={}",
                        tableName, batchKeys, tableFieldList.size(), e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > batchCostMsWarn) {
                        log.warn("table {} batch remove keys {} num {} cost: {} ms",
                                tableName, batchKeys, tableFieldList.size(), costMs);
                    }
                }));
    }

    @Override
    public boolean insert(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        Object key = mysqlReq.getSingleTableKey();
        String sql = resolveSql(dbReq);
        long begin = System.nanoTime();
        Mono<Boolean> operation = Mono.usingWhen(
                        logger.openWriteConnection(),
                        connection -> executeWrite(connection, sql, List.of(mysqlReq.getSingleTableField()))
                                .thenReturn(Boolean.TRUE),
                        Connection::close
                )
                .timeout(operationTimeout)
                .onErrorResume(e -> {
                    log.error("insert error, table={}, key={}",
                            tableName, key, e);
                    return Mono.just(Boolean.FALSE);
                })
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} insert key {} cost: {} ms", tableName, key, costMs);
                    }
                });
        return await(operation);
    }

    @Override
    public void remove(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        Object key = mysqlReq.getSingleTableKey();
        String sql = resolveSql(dbReq);
        long begin = System.nanoTime();
        Mono<Void> operation = Mono.usingWhen(
                logger.openWriteConnection(),
                connection -> executeWrite(connection, sql, List.of(mysqlReq.getSingleTableField())),
                Connection::close
        );
        await(operation.timeout(operationTimeout)
                .doOnError(e -> log.error("remove error, table={}, key={}", tableName, key, e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} remove key {} cost: {} ms", tableName, key, costMs);
                    }
                }));
    }

    @Override
    public DBRsp find(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        Object key = mysqlReq.getSingleTableKey();
        String sql = resolveSql(dbReq);
        long begin = System.nanoTime();
        Mono<DBRsp> operation = Mono.usingWhen(
                logger.openReadConnection(),
                connection -> {
                    Statement statement = connection.createStatement(sql);
                    List<DbTableField> queryArgs = List.of(mysqlReq.getSingleTableField());
                    int paramIndex = 0;
                    for (DbTableField tableField : queryArgs) {
                        List<DbValue> valueList = tableField.getValueList();
                        if (valueList == null || valueList.isEmpty()) {
                            continue;
                        }
                        for (DbValue dbValue : valueList) {
                            statement.bind(paramIndex++, dbValue.getV());
                        }
                    }
                    return Flux.from(statement.execute())
                            .flatMap(result -> result.map((row, metadata) -> {
                                List<DbValue> resultList = new ArrayList<>();
                                int columnIndex = 0;
                                for (var ignored : metadata.getColumnMetadatas()) {
                                    resultList.add(new DbValue(row.get(columnIndex++)));
                                }
                                DbTableField rowField = new DbTableField();
                                rowField.setValueList(resultList);
                                return rowField;
                            }))
                            .collectList()
                            .map(rows -> {
                                MysqlRsp mysqlRsp = new MysqlRsp();
                                mysqlRsp.setTablFieldList(rows == null ? new ArrayList<>() : rows);
                                DBRsp dbRsp = new DBRsp();
                                dbRsp.setSuccess(true);
                                dbRsp.setMysqlRsp(mysqlRsp);
                                return dbRsp;
                            });
                },
                Connection::close
        );
        return await(operation.timeout(operationTimeout)
                .doOnError(e -> log.error("find error, table={}, key={}", tableName, key, e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} find key {} cost: {} ms", tableName, key, costMs);
                    }
                }));
    }

    @Override
    public DBRsp findBatch(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        String batchKeys = getBatchKeys(mysqlReq);
        String sql = resolveSql(dbReq);
        long begin = System.nanoTime();
        Mono<DBRsp> operation = Mono.usingWhen(
                logger.openReadConnection(),
                connection -> {
                    Statement statement = connection.createStatement(sql);
                    int paramIndex = 0;
                    for (DbTableField tableField : mysqlReq.getTablFieldList()) {
                        statement.bind(paramIndex++, tableField.getTableKey());
                    }
                    return Flux.from(statement.execute())
                            .flatMap(result -> result.map((row, metadata) -> {
                                List<DbValue> resultList = new ArrayList<>();
                                int columnIndex = 0;
                                for (var ignored : metadata.getColumnMetadatas()) {
                                    resultList.add(new DbValue(row.get(columnIndex++)));
                                }
                                DbTableField rowField = new DbTableField();
                                rowField.setValueList(resultList);
                                return rowField;
                            }))
                            .collectList()
                            .map(rows -> {
                                MysqlRsp mysqlRsp = new MysqlRsp();
                                mysqlRsp.setTablFieldList(rows == null ? new ArrayList<>() : rows);
                                DBRsp dbRsp = new DBRsp();
                                dbRsp.setSuccess(true);
                                dbRsp.setMysqlRsp(mysqlRsp);
                                return dbRsp;
                            });
                },
                Connection::close
        );
        return await(operation.timeout(operationTimeout)
                .doOnError(e -> log.error("find batch error, table={}, keys={}", tableName, batchKeys, e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > batchCostMsWarn) {
                        log.warn("table {} batch find keys {} cost: {} ms", tableName, batchKeys, costMs);
                    }
                }));
    }

    @Override
    public boolean detect() {
        Mono<Boolean> operation = Mono.usingWhen(
                        logger.openWriteConnection(),
                        connection -> Mono.from(connection.validate(io.r2dbc.spi.ValidationDepth.REMOTE)),
                        Connection::close
                )
                .cast(Boolean.class)
                .timeout(operationTimeout)
                .onErrorResume(e -> {
                    log.error("detect error", e);
                    return Mono.just(Boolean.FALSE);
                });
        return await(operation);
    }

    @Override
    public void close() {
        logger.close();
    }

    @Override
    public List<TRecord> createTRecord(DBReq _dbReq) {
        ArrayList<TRecord> returnList = new ArrayList<>();
        for (DbTableField dbTableField : _dbReq.getMysqlReq().getTablFieldList()) {
            DbOpType dbOpType =
                    // 这里映射成批量的，下面就不用处理了
                    switch (_dbReq.getDbOpType()) {
                        case SAVE -> DbOpType.BATCH_SAVE;
                        case REMOVE -> DbOpType.BATCH_REMOVE;
                        default -> _dbReq.getDbOpType();
                    };

            TRecord tRecord = new MysqlTRecord(dbOpType, dbTableField);
            returnList.add(tRecord);
        }
        return returnList;
    }

    @Override
    public String parseTableName(DBReq _dbReq) {
        return _dbReq.getMysqlReq().getTableName();
    }

    @Override
    public List<DBReq> buildSaveDBReq(String tableName, Collection<TRecord> records) {
        List<DBReq> dbReqList = new ArrayList<>();

        Map<DbOpType, List<MysqlTRecord>> collect = records.stream()
                .map(a -> (MysqlTRecord) a)
                .collect(Collectors.groupingBy(MysqlTRecord::getDbOpType));

        for (Map.Entry<DbOpType, List<MysqlTRecord>> entry : collect.entrySet()) {
            DbOpType key = entry.getKey();
            List<MysqlTRecord> value = entry.getValue();

            DBReq dbReq = new DBReq();
            dbReq.setDbOpType(key);
            MysqlReq mysqlReq = new MysqlReq();
            dbReq.setMysqlReq(mysqlReq);

            mysqlReq.setTableName(tableName);
            for (MysqlTRecord mysqlTRecord : value) {
                mysqlReq.getTablFieldList().add(mysqlTRecord.getDbTableField());
            }

            dbReqList.add(dbReq);
        }
        return dbReqList;
    }


    @Override
    public DBRsp cache(DBReq dbReq, DBCache dbCache) {
        if (dbCache == null) {
            return null;
        }
        TableCache tableCache = dbCache.getTableCache(dbReq.getMysqlReq().getTableName());
        switch (dbReq.getDbOpType()) {
            case GET:
            case BATCH_GET:
                DBRsp dbRsp = new DBRsp();
                MysqlRsp mysqlRsp = new MysqlRsp();
                dbRsp.setMysqlRsp(mysqlRsp);
                dbRsp.setSuccess(true);
                ArrayList<DbTableField> notFindList = new ArrayList<>();
                for (DbTableField dbTableField : dbReq.getMysqlReq().getTablFieldList()) {
                    Object tableKey = dbTableField.getTableKey();
                    MysqlTRecord tRecord = (MysqlTRecord) tableCache.getCache().get(tableKey);
                    if (tRecord != null) {
                        if(DbOpType.isRemove(tRecord.getDbOpType())) {
                            mysqlRsp.getTablFieldList().add(new DbTableField());
                        } else {
                            mysqlRsp.getTablFieldList().add(tRecord.getDbTableField());
                        }
                    } else {
                        notFindList.add(dbTableField);
                    }
                }
                // 将没在缓存里的其他查询一下;
                if (!notFindList.isEmpty()) {
                    DBReq copyDBReq = new DBReq();
                    copyDBReq.setDbOpType(dbReq.getDbOpType());
                    copyDBReq.setMysqlReq(new MysqlReq());
                    copyDBReq.getMysqlReq().setTablFieldList(notFindList);
                    copyDBReq.getMysqlReq().setTableName(dbReq.getMysqlReq().getTableName());
                    DBRsp batch = null;
                    if (dbReq.getDbOpType() == DbOpType.GET) {
                        batch = find(copyDBReq);
                    } else if (dbReq.getDbOpType() == DbOpType.BATCH_GET) {
                        batch = findBatch(copyDBReq);
                    }
                    mysqlRsp.getTablFieldList().addAll(batch.getMysqlRsp().getTablFieldList());
                }
                return dbRsp;
            case SAVE:
            case BATCH_SAVE:
            case REMOVE:
            case BATCH_REMOVE:
                ArrayList<TRecord> returnList = new ArrayList<>();
                for (DbTableField dbTableField : dbReq.getMysqlReq().getTablFieldList()) {
                    DbOpType dbOpType =
                            // 这里映射成批量的，下面就不用处理了
                            switch (dbReq.getDbOpType()) {
                                case SAVE -> DbOpType.BATCH_SAVE;
                                case REMOVE -> DbOpType.BATCH_REMOVE;
                                default -> dbReq.getDbOpType();
                            };

                    TRecord tRecord = new MysqlTRecord(dbOpType, dbTableField);
                    returnList.add(tRecord);
                }
                for (TRecord record : returnList) {
                    tableCache.getCache().put(record.getKey(), record);
                }
                dbRsp = new DBRsp();
                mysqlRsp = new MysqlRsp();
                dbRsp.setMysqlRsp(mysqlRsp);
                dbRsp.setSuccess(true);
                return dbRsp;
            default: return null;

        }
    }

    private Mono<Void> executeWrite(Connection connection, String sql, List<DbTableField> tableFieldList) {
        Statement statement = connection.createStatement(sql);
        bindBatchTableFields(statement, tableFieldList);
        return Flux.from(statement.execute())
                .flatMap(Result::getRowsUpdated)
                .then();
    }

    private void bindBatchTableFields(Statement statement, List<DbTableField> tableFieldList) {
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return;
        }
        for (int rowIndex = 0; rowIndex < tableFieldList.size(); rowIndex++) {
            if (rowIndex > 0) {
                statement.add();
            }
            bindValues(statement, tableFieldList.get(rowIndex).getValueList());
        }
    }

    private void bindValues(Statement statement, List<DbValue> valueList) {
        if (valueList == null || valueList.isEmpty()) {
            return;
        }
        for (int i = 0; i < valueList.size(); i++) {
            statement.bind(i, valueList.get(i).getV());
        }
    }

    private <T> T await(Mono<T> operation) {
        return dbService.awaitDb(operation, dbOperationTimeoutMillis);
    }

    private String resolveSql(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        DbOpType dbOpType = dbReq.getDbOpType();
        if (dbOpType == DbOpType.CREATE_TABLE) {
            return mysqlReq.getSql();
        }


        TableMeta tableMeta = tableMetaCache.get(mysqlReq.getTableName());
        if (tableMeta == null) {
            throw new IllegalArgumentException("table meta 未注册: " + mysqlReq.getTableName());
        }
        return switch (dbOpType) {
            case GET -> "SELECT " + joinColumnNames(tableMeta.columnNames())
                    + " FROM " + tableMeta.tableName() + " WHERE " + tableMeta.keyColumnName() + " = ?";
            case BATCH_GET -> "SELECT " + joinColumnNames(tableMeta.columnNames())
                    + " FROM " + tableMeta.tableName() + " WHERE " + tableMeta.keyColumnName()
                    + " IN (" + createPlaceholders(mysqlReq.getTablFieldList().size()) + ")";
            case SAVE, BATCH_SAVE -> "REPLACE INTO " + tableMeta.tableName() + " ("
                    + joinColumnNames(tableMeta.columnNames()) + ") VALUES ("
                    + createPlaceholders(tableMeta.columnNames().size()) + ")";
            case REMOVE -> "DELETE FROM " + tableMeta.tableName() + " WHERE " + tableMeta.keyColumnName() + " = ?";
            case BATCH_REMOVE -> "DELETE FROM " + tableMeta.tableName() + " WHERE " + tableMeta.keyColumnName()
                    + " IN (" + createPlaceholders(mysqlReq.getTablFieldList().size()) + ")";
            default -> throw new IllegalArgumentException("unsupported db op type: " + dbOpType);
        };
    }

    private String joinColumnNames(List<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException("columnNames 不能为空");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(columnNames.get(i));
        }
        return builder.toString();
    }

    private String createPlaceholders(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("placeholder 数量必须大于 0");
        }
        StringBuilder builder = new StringBuilder(Math.max(1, count * 3 - 1));
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("?");
        }
        return builder.toString();
    }

    private String getBatchKeys(MysqlReq mysqlReq) {
        if (mysqlReq.getTablFieldList() == null || mysqlReq.getTablFieldList().isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < mysqlReq.getTablFieldList().size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(mysqlReq.getTableKey(mysqlReq.getTablFieldList().get(i)));
        }
        builder.append(']');
        return builder.toString();
    }

    private record TableMeta(String tableName, String keyColumnName, List<String> columnNames) {
        private TableMeta {
            Objects.requireNonNull(tableName, "tableName 不能为空");
            Objects.requireNonNull(keyColumnName, "keyColumnName 不能为空");
            Objects.requireNonNull(columnNames, "columnNames 不能为空");
        }
    }
}
