package org.evd.game.DBService.storage.mysql;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StorageMysql implements StorageEngine {
    private static final Logger log = LoggerFactory.getLogger(StorageMysql.class);
    private static final String VERSION_COLUMN_NAME = "version";

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
    public void replaceBatch(DBReq dbReq) {
        executeBatchWrite(dbReq, "replace", this::executeReplaceBatch);
    }

    @Override
    public void replace(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        Object key = mysqlReq.getSingleTableKey();
        long begin = System.nanoTime();
        Mono<Void> operation = executeWithWriteTransaction(connection ->
                        executeReplace(connection, tableName, mysqlReq.getSingleTableField()),
                        operationTimeout)
                .doOnError(error -> log.error("replace error, table={}, key={}", tableName, key, error))
                .onErrorMap(SysException::new)
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > costMsWarn) {
                        log.warn("table {} replace key {} cost: {} ms", tableName, key, costMs);
                    }
                });
        await(logger.executeSerial(key, () -> operation));
    }

    private void executeBatchWrite(DBReq dbReq,
                                   String operationName,
                                   BatchWrite operation) {
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
                        long begin = System.nanoTime();
                        return executeWithWriteTransaction(connection ->
                                operation.execute(connection, tableName, fields),
                                batchOperationTimeout)
                                .doOnError(e -> log.error("{} batch error, table={}, keys={}, num={}",
                                        operationName, tableName, batchKeys, fields.size(), e))
                                .onErrorMap(SysException::new)
                                .doFinally(signalType -> {
                                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                                    if (costMs > batchCostMsWarn) {
                                        log.warn("table {} batch {} keys {} num {} cost: {} ms",
                                                tableName, operationName, batchKeys, fields.size(), costMs);
                                    }
                                });
                    }));
    }

    @Override
    public void executeTransaction(List<DBReq> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("transaction requests 不能为空");
        }
        if (requests.isEmpty()) {
            return;
        }
        long begin = System.nanoTime();
        Mono<Void> operation = executeWithWriteTransaction(connection ->
                Flux.fromIterable(requests)
                        .concatMap(request -> executeTransactionRequest(connection, request))
                        .then(),
                batchOperationTimeout)
                .doOnError(error -> log.error("execute transaction failed, requestCount={}",
                        requests.size(), error))
                .doFinally(signalType -> {
                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                    if (costMs > batchCostMsWarn) {
                        log.warn("execute transaction requestCount={} cost: {} ms",
                                requests.size(), costMs);
                    }
                });
        List<Object> transactionKeys = collectTransactionKeys(requests);
        awaitBatch(logger.executeSerialTransaction(transactionKeys, () -> operation));
    }

    private List<Object> collectTransactionKeys(List<DBReq> requests) {
        List<Object> keys = new ArrayList<>();
        for (DBReq request : requests) {
            if (request == null || request.getDbOpType() == null || request.getMysqlReq() == null) {
                throw new IllegalArgumentException("transaction 子请求不完整");
            }
            MysqlReq mysqlReq = request.getMysqlReq();
            switch (request.getDbOpType()) {
                case SAVE, REMOVE -> keys.add(
                        mysqlReq.getSingleTableField().getTableKey());
                case BATCH_SAVE, BATCH_REMOVE -> {
                    List<DbTableField> fields = mysqlReq.getTablFieldList();
                    if (fields == null) {
                        throw new IllegalArgumentException("transaction 批量字段列表不能为空");
                    }
                    for (DbTableField field : fields) {
                        keys.add(field.getTableKey());
                    }
                }
                default -> throw new IllegalArgumentException(
                        "transaction 不支持子操作: " + request.getDbOpType());
            }
        }
        return keys;
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
                        long begin = System.nanoTime();
                        return executeWithWriteTransaction(connection ->
                                executeRemovesInChunks(connection, tableName, fields),
                                batchOperationTimeout)
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
    public void remove(DBReq dbReq) {
        MysqlReq mysqlReq = dbReq.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        Object key = mysqlReq.getSingleTableKey();
        long begin = System.nanoTime();
        Mono<Void> operation = executeWithWriteTransaction(connection ->
                executeRemovesInChunks(connection, tableName,
                        List.of(mysqlReq.getSingleTableField())), operationTimeout);
        await(logger.executeSerial(key,
                () -> operation
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
                            .flatMap(result -> result.map(this::readTableField))
                            .collectList()
                            .map(rows -> {
                                MysqlRsp mysqlRsp = new MysqlRsp();
                                mysqlRsp.setTablFieldList(rows == null ? new ArrayList<>() : rows);
                                DBRsp dbRsp = new DBRsp();
                                dbRsp.setSuccess(true);
                                dbRsp.setMysqlRsp(mysqlRsp);
                                return dbRsp;
                            })
                            .timeout(operationTimeout);
                },
                Connection::close
        );
        return await(logger.executeSerial(key,
                () -> operation
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
                            .flatMap(result -> result.map(this::readTableField))
                            .collectList()
                            .map(rows -> {
                                MysqlRsp mysqlRsp = new MysqlRsp();
                                mysqlRsp.setTablFieldList(rows == null ? new ArrayList<>() : rows);
                                DBRsp dbRsp = new DBRsp();
                                dbRsp.setSuccess(true);
                                dbRsp.setMysqlRsp(mysqlRsp);
                                return dbRsp;
                            })
                            .timeout(batchOperationTimeout);
                },
                Connection::close
                    );
                        return operation
                                .doOnError(e -> log.error("find batch error, table={}, keys={}",
                                        tableName, batchKeys, e))
                                .onErrorMap(SysException::new)
                                .doFinally(signalType -> {
                                    long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
                                    if (costMs > batchCostMsWarn) {
                                        log.warn("table {} batch find keys {} cost: {} ms",
                                                tableName, batchKeys, costMs);
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

    private Mono<Void> executeWithWriteTransaction(Function<Connection, Mono<Void>> work,
                                                  Duration timeout) {
        return Mono.defer(() -> {
            TransactionState state = new TransactionState();
            return Mono.usingWhen(
                    logger.openWriteConnection(),
                    connection -> Mono.defer(() -> Mono.from(connection.beginTransaction()))
                            .then(Mono.defer(() -> {
                                Mono<Void> transactionWork = Objects.requireNonNull(
                                        work.apply(connection), "transaction work returned null");
                                return transactionWork.doOnSubscribe(
                                        ignored -> state.workStarted.set(true));
                            }))
                            .then(Mono.defer(() -> {
                                state.commitStarted.set(true);
                                return Mono.from(connection.commitTransaction())
                                        .doOnSuccess(ignored -> state.commitConfirmed.set(true));
                            }))
                            .timeout(timeout)
                            .onErrorMap(error -> state.commitStarted.get()
                                    && !state.commitConfirmed.get()
                                    ? new DbSerialExecutor.OutcomeUnknownException(
                                            "MySQL commit started but its outcome is unknown", error)
                                    : error),
                    connection -> Mono.defer(() -> Mono.from(connection.close()))
                            .onErrorResume(closeFailure -> {
                                log.error("MySQL transaction committed but connection close failed",
                                        closeFailure);
                                return Mono.empty();
                            }),
                    (connection, failure) -> rollbackAndClose(connection, failure, state),
                    connection -> rollbackAndClose(connection, null, state)
            );
        });
    }

    /**
     * 事务流程失败或被取消后，尝试回滚并关闭连接。
     * @param connection 当前事务使用的 MySQL 连接
     * @param operationFailure 触发清理的原始异常；取消路径可能为 null
     * @param state 当前事务工作和提交阶段的状态
     */
    private Mono<Void> rollbackAndClose(Connection connection,
                                        Throwable operationFailure,
                                        TransactionState state) {
        // 延迟执行回滚，等 usingWhen 订阅清理 Publisher 时才真正调用数据库。
        return Mono.defer(() -> Mono.from(connection.rollbackTransaction()))
                // 回滚失败时转入兜底流程：判断结果是否未知、关闭连接并返回回滚错误。
                .onErrorResume(rollbackFailure -> closeAfterRollbackFailure(
                        // 回滚和关闭都使用当前事务持有的同一个连接。
                        // operationFailure 是触发清理的原始失败；取消清理时它可能为 null。
                        // rollbackFailure 是本次回滚失败的原因。
                        // state 记录事务工作和提交是否已经开始或确认。
                        connection, operationFailure, rollbackFailure, state))
                // 回滚成功后继续关闭连接；回滚失败时兜底流程会返回错误，不会走到这里。
                .then(Mono.defer(() -> Mono.from(connection.close()))
                        // 关闭失败时包装成清理异常，并保留可能存在的原始操作异常。
                        .onErrorMap(closeFailure -> cleanupFailure(
                                // 说明连接关闭发生在事务回滚之后。
                                "MySQL connection close failed after transaction rollback",
                                // 原始业务/数据库操作异常；没有原始异常时以关闭异常为主因。
                                // 当前连接关闭失败异常会由 cleanupFailure 记录并附加。
                                operationFailure, closeFailure)));
    }

    /**
     * 回滚失败后的兜底处理：标记数据库结果、尽力关闭连接并报告回滚失败。
     * @param connection 当前事务使用的 MySQL 连接
     * @param operationFailure 触发清理的原始异常；取消路径可能为 null
     * @param rollbackFailure 回滚操作本身抛出的异常
     * @param state 当前事务工作和提交阶段的状态
     */
    private Mono<Void> closeAfterRollbackFailure(Connection connection,
                                                 Throwable operationFailure,
                                                 Throwable rollbackFailure,
                                                 TransactionState state) {
        // 工作已开始，说明事务内可能已经执行 SQL，回滚失败后无法确认数据库状态。
        boolean outcomeUnknown = state.workStarted.get()
                // 提交已开始但没有确认成功时，提交可能已在 MySQL 生效，因此结果未知。
                || (state.commitStarted.get() && !state.commitConfirmed.get());
        // 提交结果不明时使用更具体的错误信息；其余情况按是否执行过事务工作区分。
        String failureMessage = state.commitStarted.get() && !state.commitConfirmed.get()
                // 提交和回滚都无法确认时，无法判断事务是否已生效。
                ? "MySQL commit outcome and rollback outcome are unknown"
                // 执行过事务工作但回滚失败，数据库最终状态无法确认。
                : outcomeUnknown
                        ? "MySQL transaction rollback failed; database outcome is unknown"
                        // 事务工作还没开始，回滚失败不会包含已执行的业务 SQL。
                        : "MySQL transaction rollback failed before transaction work started";
        // 优先保留触发清理的原始异常；如果没有原始异常，就以回滚异常作为根因。
        Throwable cause = operationFailure == null ? rollbackFailure : operationFailure;
        // 结果未知时用专用异常，让上层知道不能把这次写入简单当成失败或成功。
        RuntimeException rollbackError = outcomeUnknown
                ? new DbSerialExecutor.OutcomeUnknownException(failureMessage, cause)
                // 结果可确定时用普通运行时异常报告回滚失败。
                : new RuntimeException(failureMessage, cause);
        // 原始异常已作为根因时，把回滚异常也附上，避免丢失第二个故障原因。
        if (operationFailure != null && rollbackFailure != operationFailure) {
            rollbackError.addSuppressed(rollbackFailure);
        }
        // 记录回滚失败本身，便于日志定位数据库清理故障。
        log.error("MySQL transaction rollback failed", rollbackFailure);
        // 无论回滚是否成功，都尽力关闭连接，避免连接资源继续占用。
        return Mono.defer(() -> Mono.from(connection.close()))
                // 关闭也失败时附加到回滚异常，保留两个清理故障。
                .onErrorResume(closeFailure -> {
                    rollbackError.addSuppressed(closeFailure);
                    // 记录连接关闭失败；异常已附加到最终返回的 rollbackError。
                    log.error("MySQL connection close failed after rollback failure", closeFailure);
                    // 关闭失败不覆盖回滚异常，继续返回统一的回滚错误。
                    return Mono.empty();
                })
                // 关闭处理结束后，将回滚错误交还给 usingWhen 的调用方。
                .then(Mono.error(rollbackError));
    }

    private RuntimeException cleanupFailure(String message,
                                            Throwable operationFailure,
                                            Throwable cleanupFailure) {
        String failureMessage = message;
        Throwable cause = operationFailure == null ? cleanupFailure : operationFailure;
        RuntimeException failure = new RuntimeException(failureMessage, cause);
        if (operationFailure != null && cleanupFailure != operationFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        log.error(failureMessage, cleanupFailure);
        return failure;
    }

    private Mono<Void> executeTransactionRequest(Connection connection, DBReq request) {
        if (request == null || request.getDbOpType() == null || request.getMysqlReq() == null) {
            return Mono.error(new IllegalArgumentException("transaction 子请求不完整"));
        }
        MysqlReq mysqlReq = request.getMysqlReq();
        String tableName = mysqlReq.getTableName();
        return switch (request.getDbOpType()) {
            case SAVE -> executeReplace(connection, tableName,
                    mysqlReq.getSingleTableField());
            case BATCH_SAVE -> executeReplaceBatch(connection, tableName,
                    mysqlReq.getTablFieldList());
            case REMOVE -> executeRemovesInChunks(connection, tableName,
                    List.of(mysqlReq.getSingleTableField()));
            case BATCH_REMOVE -> executeRemovesInChunks(connection, tableName,
                    mysqlReq.getTablFieldList());
            default -> Mono.error(new IllegalArgumentException(
                    "transaction 不支持子操作: " + request.getDbOpType()));
        };
    }

    private Mono<Void> executeRemovesInChunks(Connection connection,
                                               String tableName,
                                               List<DbTableField> tableFieldList) {
        if (tableFieldList == null) {
            return Mono.error(new IllegalArgumentException("BATCH_REMOVE 字段列表不能为空"));
        }
        if (tableFieldList.isEmpty()) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            List<List<DbTableField>> chunks = splitFields(tableFieldList);
            return Flux.fromIterable(chunks)
                    .concatMap(fields -> executeRemoveBatch(connection, tableName, fields))
                    .then();
        });
    }

    private Mono<Void> executeRemoveBatch(Connection connection,
                                          String tableName,
                                          List<DbTableField> tableFieldList) {
        TableMeta tableMeta = requireTableMeta(tableName);
        return executeBatchDeletes(connection, tableMeta, tableFieldList);
    }

    private Mono<Void> executeBatchDeletes(Connection connection,
                                           TableMeta tableMeta,
                                           List<DbTableField> tableFieldList) {
        if (tableFieldList.isEmpty()) {
            return Mono.empty();
        }
        String sql = "DELETE FROM " + tableMeta.tableName() + " WHERE "
                + tableMeta.keyColumnName() + " = ?";
        Statement statement = connection.createStatement(sql);
        for (int rowIndex = 0; rowIndex < tableFieldList.size(); rowIndex++) {
            if (rowIndex > 0) {
                // 保存上一组主键绑定并开始下一组；所有参数组由后续 execute() 执行。
                statement.add();
            }
            statement.bind(0, tableFieldList.get(rowIndex).getTableKey());
        }
        // DELETE 以主键为条件；记录不存在时影响零行也视为删除完成。
        return Flux.from(statement.execute())
                .flatMap(Result::getRowsUpdated)
                .then();
    }

    private List<List<DbTableField>> splitFields(List<DbTableField> tableFieldList) {
        List<DbTableField> sortedFields = new ArrayList<>(tableFieldList);
        sortedFields.sort((left, right) -> compareTableKeys(
                left.getTableKey(), right.getTableKey()));
        List<List<DbTableField>> chunks = new ArrayList<>();
        for (int start = 0; start < sortedFields.size(); start += batchPerCount) {
            int end = Math.min(start + batchPerCount, sortedFields.size());
            chunks.add(sortedFields.subList(start, end));
        }
        return chunks;
    }

    private int compareTableKeys(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Long.compare(leftNumber.longValue(), rightNumber.longValue());
        }
        if (left instanceof String leftString && right instanceof String rightString) {
            return leftString.compareTo(rightString);
        }
        throw new IllegalArgumentException("不支持的表主键类型比较: left="
                + (left == null ? "null" : left.getClass().getName()) + ", right="
                + (right == null ? "null" : right.getClass().getName()));
    }

    private Mono<Void> executeReplace(Connection connection,
                                      String tableName,
                                      DbTableField tableField) {
        return executeReplaceBatch(connection, tableName, List.of(tableField));
    }

    private Mono<Void> executeReplaceBatch(Connection connection,
                                           String tableName,
                                           List<DbTableField> tableFieldList) {
        if (tableFieldList == null) {
            return Mono.error(new IllegalArgumentException("REPLACE 字段列表不能为空"));
        }
        if (tableFieldList.isEmpty()) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            TableMeta tableMeta = requireTableMeta(tableName);
            String sql = "REPLACE INTO " + tableMeta.tableName() + " ("
                    + joinColumnNames(tableMeta.columnNames()) + ") VALUES ("
                    + createPlaceholders(tableMeta.columnNames().size()) + ")";
            List<List<DbTableField>> chunks = splitFields(tableFieldList);
            return Flux.fromIterable(chunks)
                    .concatMap(fields -> {
                        Statement statement = connection.createStatement(sql);
                        for (int rowIndex = 0; rowIndex < fields.size(); rowIndex++) {
                            if (rowIndex > 0) {
                                statement.add();
                            }
                            DbTableField tableField = fields.get(rowIndex);
                            List<DbValue> valueList = tableField.getValueList();
                            for (int columnIndex = 0; columnIndex < valueList.size(); columnIndex++) {
                                statement.bind(columnIndex, valueList.get(columnIndex).getV());
                            }
                            statement.bind(valueList.size(), tableField.getVersion());
                        }
                        return Flux.from(statement.execute())
                                .flatMap(Result::getRowsUpdated)
                                .then();
                    })
                    .then();
        });
    }

    private DbTableField readTableField(Row row, RowMetadata metadata) {
        int columnCount = metadata.getColumnMetadatas().size();
        if (columnCount == 0) {
            throw new IllegalStateException("查询结果没有列");
        }
        String lastColumnName = metadata.getColumnMetadatas().get(columnCount - 1).getName();
        if (!VERSION_COLUMN_NAME.equalsIgnoreCase(lastColumnName)) {
            throw new IllegalStateException("查询结果末列必须是 version，实际为: " + lastColumnName);
        }
        Long version = row.get(columnCount - 1, Long.class);
        if (version == null) {
            throw new IllegalStateException("查询结果 version 为空");
        }
        List<DbValue> resultList = new ArrayList<>(columnCount - 1);
        for (int columnIndex = 0; columnIndex < columnCount - 1; columnIndex++) {
            resultList.add(new DbValue(row.get(columnIndex)));
        }
        DbTableField rowField = new DbTableField();
        rowField.setValueList(resultList);
        rowField.setVersion(version);
        return rowField;
    }

    private TableMeta requireTableMeta(String tableName) {
        TableMeta tableMeta = tableMetaCache.get(tableName);
        if (tableMeta == null) {
            throw new IllegalArgumentException("table meta 未注册: " + tableName);
        }
        return tableMeta;
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
                // 保存上一条记录的绑定参数，并为下一条记录开始新的一组绑定。
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


        TableMeta tableMeta = requireTableMeta(mysqlReq.getTableName());
        return switch (dbOpType) {
            case GET -> "SELECT " + joinColumnNames(tableMeta.columnNames())
                    + " FROM " + tableMeta.tableName() + " WHERE " + tableMeta.keyColumnName() + " = ?";
            case BATCH_GET -> "SELECT " + joinColumnNames(tableMeta.columnNames())
                    + " FROM " + tableMeta.tableName() + " WHERE " + tableMeta.keyColumnName()
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
        TableMeta tableMeta = requireTableMeta(tableName);
        return switch (dbOpType) {
            case BATCH_GET -> "SELECT " + joinColumnNames(tableMeta.columnNames())
                    + " FROM " + tableMeta.tableName() + " WHERE " + tableMeta.keyColumnName()
                    + " IN (" + createPlaceholders(keyCount) + ")";
            default -> throw new IllegalArgumentException("unsupported batch db op type: " + dbOpType);
        };
    }

    private record TableMeta(String tableName, String keyColumnName, List<String> columnNames) {
        private TableMeta {
            Objects.requireNonNull(tableName, "tableName 不能为空");
            Objects.requireNonNull(keyColumnName, "keyColumnName 不能为空");
            Objects.requireNonNull(columnNames, "columnNames 不能为空");
            if (columnNames.size() < 2) {
                throw new IllegalArgumentException("表元数据必须包含业务列和末尾 version 列");
            }
            if (!VERSION_COLUMN_NAME.equalsIgnoreCase(columnNames.getLast())) {
                throw new IllegalArgumentException("表元数据最后一列必须是 version: " + tableName);
            }
            if (!columnNames.subList(0, columnNames.size() - 1).contains(keyColumnName)) {
                throw new IllegalArgumentException("表元数据不包含主键列: " + keyColumnName);
            }
        }

    }

    private static final class TransactionState {
        private final AtomicBoolean workStarted = new AtomicBoolean();
        private final AtomicBoolean commitStarted = new AtomicBoolean();
        private final AtomicBoolean commitConfirmed = new AtomicBoolean();
    }

    @FunctionalInterface
    private interface BatchWrite {
        Mono<Void> execute(Connection connection, String tableName, List<DbTableField> fields);
    }
}
