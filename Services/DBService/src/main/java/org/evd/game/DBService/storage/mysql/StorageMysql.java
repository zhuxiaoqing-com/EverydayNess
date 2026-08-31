package org.evd.game.DBService.storage.mysql;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.evd.game.DBService.DBProxy;
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
import org.evd.game.runtime.ymlconfig.DbStorageYml;
import org.evd.game.runtime.support.exception.SysException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StorageMysql implements StorageEngine {
    private static final Logger log = LoggerFactory.getLogger(StorageMysql.class);

    private final int batchPerCount;
    private final int costMsWarn;
    private final int batchCostMsWarn;
    private final Duration operationTimeout;
    private final Duration batchOperationTimeout;
    private final long dbOperationTimeoutMillis;
    private final long batchOperationTimeoutMillis;
    private final DBProxy dbProxy;
    private final LoggerMysql logger;
    /** 建表后把表结构注册下来，后续 CRUD 在这里统一拼 SQL。 */
    private final Map<String, TableMeta> tableMetaCache = new ConcurrentHashMap<>();

    public StorageMysql(DBProxy dbProxy, LoggerMysql logger, DbStorageYml storageConfig) {
        this.dbProxy = dbProxy;
        this.logger = logger;
        this.batchPerCount = storageConfig.getBatchPerCount();
        if (batchPerCount <= 0) {
            throw new IllegalArgumentException("batchPerCount must be greater than 0: " + batchPerCount);
        }
        this.costMsWarn = storageConfig.getCostMsWarn();
        this.batchCostMsWarn = storageConfig.getBatchCostMsWarn();
        this.operationTimeout = logger.getOperationTimeout();
        this.batchOperationTimeout = logger.getBatchOperationTimeout();
        this.dbOperationTimeoutMillis = logger.getOperationTimeout().toMillis();
        this.batchOperationTimeoutMillis = logger.getBatchOperationTimeout().toMillis();
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

        TableMeta tableMeta = new TableMeta(tableName, tableMetaData.getKeyColumnName(),
                List.copyOf(tableMetaData.getColumnNames()));
        if (tableMetaCache.putIfAbsent(tableName, tableMeta) == null) {
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
        await(logger.executeSerial(key, () -> operation.timeout(operationTimeout)
                .doOnError(e -> log.error("replace error, table={}, key={}", tableName, key, e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} replace key {} cost: {} ms", tableName, key, costMs);
                    }
                })));
    }

    @Override
    public void replaceBatch(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        List<DbTableField> tableFieldList = mysqlReq.getTablFieldList();
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return;
        }
        String tableName = mysqlReq.getTableName();
        String sql = resolveSql(dbReq);
        awaitBatch(logger.executeSerialBatch(tableFieldList,
                DbTableField::getTableKey,
                fields -> {
                    String batchKeys = getBatchKeys(fields);
                    long begin = System.nanoTime();
                    return Mono.usingWhen(
                        logger.openWriteConnection(),
                        connection -> {
                            if (fields.size() <= batchPerCount) {
                                return executeWrite(connection, sql, fields);
                            }
                            List<Mono<Void>> operations = new ArrayList<>();
                            for (int start = 0; start < fields.size(); start += batchPerCount) {
                                int end = Math.min(start + batchPerCount, fields.size());
                                operations.add(executeWrite(connection, sql, fields.subList(start, end)));
                            }
                            return Flux.concat(operations).then();
                        },
                        Connection::close
                    ).timeout(batchOperationTimeout)
                    .doOnError(e -> log.error("replace batch error, table={}, keys={}, num={}",
                            tableName, batchKeys, fields.size(), e))
                    .onErrorMap(SysException::new)
                    .doFinally(signalType -> {
                        long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                        if (costMs > batchCostMsWarn) {
                            log.warn("table {} batch replace keys {} num {} cost: {} ms",
                                    tableName, batchKeys, fields.size(), costMs);
                        }
                    });
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
        awaitBatch(logger.executeSerialBatch(tableFieldList,
                DbTableField::getTableKey,
                fields -> {
                    String batchKeys = getBatchKeys(fields);
                    String sql = resolveBatchSql(tableName, DbOpType.BATCH_REMOVE, fields.size());
                    long begin = System.nanoTime();
                    return Mono.usingWhen(
                        logger.openWriteConnection(),
                        connection -> {
                            Statement statement = connection.createStatement(sql);
                            if (!fields.isEmpty()) {
                                int paramIndex = 0;
                                for (DbTableField tableField : fields) {
                                    statement.bind(paramIndex++, tableField.getTableKey());
                                }
                            }
                            return Flux.from(statement.execute())
                                    .flatMap(Result::getRowsUpdated)
                                    .then();
                        },
                        Connection::close
                    ).timeout(batchOperationTimeout)
                    .doOnError(e -> log.error("remove batch error, table={}, keys={}, num={}",
                            tableName, batchKeys, fields.size(), e))
                    .onErrorMap(SysException::new)
                    .doFinally(signalType -> {
                        long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                        if (costMs > batchCostMsWarn) {
                            log.warn("table {} batch remove keys {} num {} cost: {} ms",
                                    tableName, batchKeys, fields.size(), costMs);
                        }
                    });
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
        return await(logger.executeSerial(key, () -> operation));
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
        await(logger.executeSerial(key, () -> operation.timeout(operationTimeout)
                .doOnError(e -> log.error("remove error, table={}, key={}", tableName, key, e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} remove key {} cost: {} ms", tableName, key, costMs);
                    }
                })));
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
        return await(logger.executeSerial(key, () -> operation.timeout(operationTimeout)
                .doOnError(e -> log.error("find error, table={}, key={}", tableName, key, e))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} find key {} cost: {} ms", tableName, key, costMs);
                    }
                })));
    }

    @Override
    public DBRsp findBatch(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        List<DbTableField> tableFieldList = mysqlReq.getTablFieldList();
        String tableName = mysqlReq.getTableName();
        List<DBRsp> responses = awaitBatch(logger.executeSerialBatch(tableFieldList,
                DbTableField::getTableKey,
                fields -> {
                    String batchKeys = getBatchKeys(fields);
                    String sql = resolveBatchSql(tableName, DbOpType.BATCH_GET, fields.size());
                    long begin = System.nanoTime();
                    Mono<DBRsp> operation = Mono.usingWhen(
                logger.openReadConnection(),
                connection -> {
                    Statement statement = connection.createStatement(sql);
                    int paramIndex = 0;
                    for (DbTableField tableField : fields) {
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
                    return operation.timeout(batchOperationTimeout)
                            .doOnError(e -> log.error("find batch error, table={}, keys={}", tableName, batchKeys, e))
                            .onErrorMap(SysException::new)
                            .doFinally(signalType -> {
                                long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                                if (costMs > batchCostMsWarn) {
                                    log.warn("table {} batch find keys {} cost: {} ms", tableName, batchKeys, costMs);
                                }
                            });
                }));
        return mergeBatchResponses(responses);
    }

    private DBRsp mergeBatchResponses(List<DBRsp> responses) {
        MysqlRsp mergedMysqlRsp = new MysqlRsp();
        for (DBRsp response : responses) {
            if (!response.isSuccess()) {
                return response;
            }
            MysqlRsp mysqlRsp = Objects.requireNonNull(response.getMysqlRsp(),
                    "batch response mysqlRsp 不能为空");
            mergedMysqlRsp.getTablFieldList().addAll(mysqlRsp.getTablFieldList());
        }
        DBRsp result = new DBRsp();
        result.setSuccess(true);
        result.setMysqlRsp(mergedMysqlRsp);
        return result;
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
    public List<TRecord> createTRecord(DBReq dbReq) {
        List<TRecord> records = new ArrayList<>();
        for (DbTableField tableField : dbReq.getMysqlReq().getTablFieldList()) {
            DbOpType opType = switch (dbReq.getDbOpType()) {
                case SAVE -> DbOpType.BATCH_SAVE;
                case REMOVE -> DbOpType.BATCH_REMOVE;
                default -> dbReq.getDbOpType();
            };
            records.add(new MysqlTRecord(opType, tableField));
        }
        return records;
    }

    @Override
    public String parseTableName(DBReq dbReq) {
        return dbReq.getMysqlReq().getTableName();
    }

    @Override
    public List<DBReq> buildSaveDBReq(String tableName, Collection<TRecord> records) {
        List<DBReq> requests = new ArrayList<>();
        Map<DbOpType, List<MysqlTRecord>> grouped = records.stream()
                .map(record -> (MysqlTRecord) record)
                .collect(Collectors.groupingBy(MysqlTRecord::getDbOpType));
        for (Map.Entry<DbOpType, List<MysqlTRecord>> entry : grouped.entrySet()) {
            DBReq request = new DBReq();
            request.setDbOpType(entry.getKey());
            MysqlReq mysqlReq = new MysqlReq();
            mysqlReq.setTableName(tableName);
            for (MysqlTRecord record : entry.getValue()) {
                mysqlReq.getTablFieldList().add(record.getDbTableField());
            }
            request.setMysqlReq(mysqlReq);
            requests.add(request);
        }
        return requests;
    }

    @Override
    public DBRsp cache(DBReq dbReq, DBCache dbCache) {
        if (dbCache == null) {
            return null;
        }
        TableCache tableCache = dbCache.getTableCache(dbReq.getMysqlReq().getTableName());
        switch (dbReq.getDbOpType()) {
            case GET, BATCH_GET -> {
                DBRsp dbRsp = new DBRsp();
                MysqlRsp mysqlRsp = new MysqlRsp();
                dbRsp.setMysqlRsp(mysqlRsp);
                dbRsp.setSuccess(true);
                List<DbTableField> notFound = new ArrayList<>();
                for (DbTableField tableField : dbReq.getMysqlReq().getTablFieldList()) {
                    MysqlTRecord record = (MysqlTRecord) tableCache.getCache().get(tableField.getTableKey());
                    if (record == null) {
                        notFound.add(tableField);
                    } else if (DbOpType.isRemove(record.getDbOpType())) {
                        mysqlRsp.getTablFieldList().add(new DbTableField());
                    } else {
                        mysqlRsp.getTablFieldList().add(record.getDbTableField());
                    }
                }
                if (!notFound.isEmpty()) {
                    DBReq uncachedRequest = new DBReq();
                    uncachedRequest.setDbOpType(dbReq.getDbOpType());
                    MysqlReq uncachedMysqlReq = new MysqlReq();
                    uncachedMysqlReq.setTableName(dbReq.getMysqlReq().getTableName());
                    uncachedMysqlReq.setTablFieldList(notFound);
                    uncachedRequest.setMysqlReq(uncachedMysqlReq);
                    DBRsp uncachedResponse = dbReq.getDbOpType() == DbOpType.GET
                            ? find(uncachedRequest)
                            : findBatch(uncachedRequest);
                    mysqlRsp.getTablFieldList().addAll(uncachedResponse.getMysqlRsp().getTablFieldList());
                }
                return dbRsp;
            }
            case SAVE, BATCH_SAVE, REMOVE, BATCH_REMOVE -> {
                for (TRecord record : createTRecord(dbReq)) {
                    tableCache.getCache().put(record.getKey(), record);
                }
                DBRsp dbRsp = new DBRsp();
                dbRsp.setMysqlRsp(new MysqlRsp());
                dbRsp.setSuccess(true);
                return dbRsp;
            }
            default -> {
                return null;
            }
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
        return dbProxy.awaitDb(operation, dbOperationTimeoutMillis);
    }

    private <T> T awaitBatch(Mono<T> operation) {
        return dbProxy.awaitDb(operation, batchOperationTimeoutMillis);
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

    private String getBatchKeys(List<DbTableField> tableFieldList) {
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < tableFieldList.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(tableFieldList.get(i).getTableKey());
        }
        builder.append(']');
        return builder.toString();
    }

    private String resolveBatchSql(String tableName, DbOpType dbOpType, int keyCount) {
        TableMeta tableMeta = tableMetaCache.get(tableName);
        if (tableMeta == null) {
            throw new IllegalArgumentException("table meta 未注册: " + tableName);
        }
        return switch (dbOpType) {
            case BATCH_GET -> "SELECT " + joinColumnNames(tableMeta.columnNames())
                    + " FROM " + tableMeta.tableName() + " WHERE " + tableMeta.keyColumnName()
                    + " IN (" + createPlaceholders(keyCount) + ")";
            case BATCH_REMOVE -> "DELETE FROM " + tableMeta.tableName()
                    + " WHERE " + tableMeta.keyColumnName()
                    + " IN (" + createPlaceholders(keyCount) + ")";
            default -> throw new IllegalArgumentException("unsupported batch db op type: " + dbOpType);
        };
    }

    private record TableMeta(String tableName, String keyColumnName, List<String> columnNames) {
        private TableMeta {
            Objects.requireNonNull(tableName, "tableName 不能为空");
            Objects.requireNonNull(keyColumnName, "keyColumnName 不能为空");
            Objects.requireNonNull(columnNames, "columnNames 不能为空");
        }
    }
}
