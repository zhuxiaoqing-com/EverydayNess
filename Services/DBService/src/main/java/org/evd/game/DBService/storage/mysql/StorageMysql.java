package org.evd.game.DBService.storage.mysql;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.evd.game.DBService.DBService;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DbTableField;
import org.evd.game.runtime.Db.serialize.DbValue;
import org.evd.game.runtime.Db.serialize.MysqlReq;
import org.evd.game.runtime.Db.serialize.MysqlRsp;
import org.evd.game.runtime.config.DbStorageConfig;
import org.evd.game.runtime.support.SysException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class StorageMysql implements StorageEngine {
    private static final Logger log = LoggerFactory.getLogger(StorageMysql.class);

    private final int batchPerCount;
    private final int costMsWarn;
    private final int batchCostMsWarn;
    private final Duration operationTimeout;
    private final long dbOperationTimeoutMillis;
    private final DBService dbService;
    private final LoggerMysql logger;

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
        long begin = System.nanoTime();
        Mono<Void> operation;
        if (logger.isAutoCreate()) {
            operation = Mono.usingWhen(
                    logger.openWriteConnection(),
                    connection -> executeWrite(connection, mysqlReq.getSql(), mysqlReq.getTablFieldList()),
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
    }

    @Override
    public void replace(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        Object key = mysqlReq.getSingleTableKey();
        long begin = System.nanoTime();
        Mono<Void> operation = Mono.usingWhen(
                logger.openWriteConnection(),
                connection -> executeWrite(connection, mysqlReq.getSql(), List.of(mysqlReq.getSingleTableField())),
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
        long begin = System.nanoTime();
        Mono<Void> operation = Mono.usingWhen(
                logger.openWriteConnection(),
                connection -> executeWriteInChunks(connection, mysqlReq.getSql(), tableFieldList),
                Connection::close
        );
        await(operation.timeout(operationTimeout)
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
        long begin = System.nanoTime();
        Mono<Void> operation = Mono.usingWhen(
                logger.openWriteConnection(),
                connection -> executeWriteSequential(connection, mysqlReq.getSql(), tableFieldList),
                Connection::close
        );
        await(operation.timeout(operationTimeout)
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
        long begin = System.nanoTime();
        Mono<Boolean> operation = Mono.usingWhen(
                        logger.openWriteConnection(),
                        connection -> executeWrite(connection, mysqlReq.getSql(), List.of(mysqlReq.getSingleTableField()))
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
        long begin = System.nanoTime();
        Mono<Void> operation = Mono.usingWhen(
                logger.openWriteConnection(),
                connection -> executeWrite(connection, mysqlReq.getSql(), List.of(mysqlReq.getSingleTableField())),
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
        long begin = System.nanoTime();
        Mono<DBRsp> operation = Mono.usingWhen(
                logger.openReadConnection(),
                connection -> {
                    Statement statement = connection.createStatement(mysqlReq.getSql());
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
                            .map(this::toRsp);
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
        long begin = System.nanoTime();
        Mono<DBRsp> operation = Mono.usingWhen(
                logger.openReadConnection(),
                connection -> {
                    Statement statement = connection.createStatement(mysqlReq.getSql());
                    int paramIndex = 0;
                    for (DbTableField tableField : mysqlReq.getTablFieldList()) {
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
                            .map(this::toRsp);
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

    private Mono<Void> executeWrite(Connection connection, String sql, List<DbTableField> tableFieldList) {
        Statement statement = connection.createStatement(sql);
        bindBatchTableFields(statement, tableFieldList);
        return Flux.from(statement.execute())
                .flatMap(Result::getRowsUpdated)
                .then();
    }

    private Mono<Void> executeWriteSequential(Connection connection, String sql, List<DbTableField> tableFieldList) {
        Statement statement = connection.createStatement(sql);
        bindSequentialTableFields(statement, tableFieldList);
        return Flux.from(statement.execute())
                .flatMap(Result::getRowsUpdated)
                .then();
    }

    private Mono<Void> executeWriteInChunks(Connection connection, String sql, List<DbTableField> tableFieldList) {
        if (tableFieldList.size() <= batchPerCount) {
            return executeWrite(connection, sql, tableFieldList);
        }
        List<Mono<Void>> operations = new ArrayList<>();
        for (int start = 0; start < tableFieldList.size(); start += batchPerCount) {
            int end = Math.min(start + batchPerCount, tableFieldList.size());
            operations.add(executeWrite(connection, sql, tableFieldList.subList(start, end)));
        }
        return Flux.concat(operations).then();
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

    private void bindSequentialTableFields(Statement statement, List<DbTableField> tableFieldList) {
        if (tableFieldList == null || tableFieldList.isEmpty()) {
            return;
        }
        int paramIndex = 0;
        for (DbTableField tableField : tableFieldList) {
            List<DbValue> valueList = tableField.getValueList();
            if (valueList == null || valueList.isEmpty()) {
                continue;
            }
            for (DbValue dbValue : valueList) {
                statement.bind(paramIndex++, dbValue.getV());
            }
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

    private DBRsp toRsp(List<DbTableField> rows) {
        MysqlRsp mysqlRsp = new MysqlRsp();
        mysqlRsp.setTablFieldList(rows == null ? new ArrayList<>() : rows);
        DBRsp dbRsp = new DBRsp();
        dbRsp.setSuccess(true);
        dbRsp.setMysqlRsp(mysqlRsp);
        return dbRsp;
    }

    private <T> T await(Mono<T> operation) {
        return dbService.awaitDb(operation, dbOperationTimeoutMillis);
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
}
