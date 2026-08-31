package org.evd.game.DBService.storage.mysql;

import io.asyncer.r2dbc.mysql.MySqlConnectionFactoryProvider;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.evd.game.runtime.ymlconfig.DbMysqlRuntimeYml;
import org.evd.game.runtime.ymlconfig.DbMysqlYml;
import org.evd.game.runtime.support.exception.SysException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

public class LoggerMysql implements LoggerEngine {

    private static final Logger logger = LoggerFactory.getLogger(LoggerMysql.class);

    private final String databaseUrl;
    private final String bootstrapUrl;
    private final String database;
    private final boolean autoCreate;
    private final String username;
    private final String password;
    private final String validationQuery;
    private final Duration connectionTimeout;
    private final Duration operationTimeout;
    private final Duration batchOperationTimeout;
    private final int poolInitialSize;
    private final int poolMaxSize;
    private final Duration poolMaxIdleTime;
    private final int r2dbcIoWorkerCount;
    private final LoopResources loopResources;
    private final DbSerialExecutor serialExecutor;
    private final ConnectionFactory bootstrapConnectionFactory;
    private final ConnectionPool connectionPool;

    public LoggerMysql(DbMysqlYml config, int poolInitialSize, int poolMaxSize) {
        try {
            this.autoCreate = config.isAutoCreate();
            this.database = config.getDatabase();
            this.username = config.getUsername();
            this.password = config.getPassword();
            this.validationQuery = config.getTestQuery();
            this.connectionTimeout = Duration.ofMillis(config.getConnectionTimeoutMs());
            this.operationTimeout = Duration.ofMillis(config.getOperationTimeoutMs());
            this.batchOperationTimeout = Duration.ofMillis(config.getBatchOperationTimeoutMs());
            if (poolInitialSize < 1 || poolMaxSize < poolInitialSize) {
                throw new IllegalArgumentException("invalid mysql pool size: initial="
                        + poolInitialSize + ", max=" + poolMaxSize);
            }
            this.poolInitialSize = poolInitialSize;
            this.poolMaxSize = poolMaxSize;
            this.poolMaxIdleTime = Duration.ofMillis(config.getPoolMaxIdleTimeMs());
            DbMysqlRuntimeYml runtimeConfig = config.getRuntime();
            this.r2dbcIoWorkerCount = runtimeConfig.getR2dbcIoWorkerCount();
            this.loopResources = LoopResources.create(
                    "r2dbc-mysql", LoopResources.DEFAULT_IO_SELECT_COUNT, r2dbcIoWorkerCount, true);
            this.databaseUrl = requireUrl(config.getResolvedR2dbcUrl());
            this.bootstrapUrl = autoCreate ? stripDatabase(databaseUrl) : databaseUrl;
            this.bootstrapConnectionFactory = createConnectionFactory(bootstrapUrl);
            checkDatabase(database);
            this.connectionPool = createConnectionPool(databaseUrl);
            this.serialExecutor = new DbSerialExecutor(
                    runtimeConfig.getSerialLaneCount(), runtimeConfig.getSerialMaxPendingPerLane());
            logger.info("MySQL R2DBC pool init OK, autoCreate={}, initialSize={}, maxSize={}, ioWorkerCount={}",
                    autoCreate, poolInitialSize, poolMaxSize, r2dbcIoWorkerCount);
        } catch (Exception e) {
            logger.error("LoggerMysql error", e);
            throw new SysException(e);
        }
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public Duration getOperationTimeout() {
        return operationTimeout;
    }

    public Duration getBatchOperationTimeout() {
        return batchOperationTimeout;
    }

    public Mono<Connection> openReadConnection() {
        return Mono.from(connectionPool.create())
                .cast(Connection.class)
                .timeout(connectionTimeout);
    }

    public Mono<Connection> openWriteConnection() {
        return Mono.from(connectionPool.create())
                .cast(Connection.class)
                .timeout(connectionTimeout);
    }

    public <T> Mono<T> executeSerial(Object key, Supplier<Mono<T>> operation) {
        return serialExecutor.submit(key, operation);
    }

    public <T, R> Mono<List<R>> executeSerialBatch(List<T> items,
                                                     Function<T, Object> keyExtractor,
                                                     Function<List<T>, Mono<R>> operation) {
        return serialExecutor.submitBatch(items, keyExtractor, operation);
    }

    private void checkDatabase(String databaseName) {
        Boolean exists = Mono.usingWhen(
                        openBootstrapConnection(),
                        connection -> Flux.from(connection.createStatement(
                                                "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?")
                                        .bind(0, databaseName)
                                        .execute())
                                .flatMap(result -> result.map((row, metadata) -> row.get(0, String.class)))
                                .hasElements(),
                        Connection::close
                )
                .timeout(operationTimeout)
                .block(operationTimeout);

        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        if (!autoCreate) {
            throw new SysException("database is not inited, please execute initDB.sql into mysql.");
        }

        String sql = "CREATE DATABASE IF NOT EXISTS `" + escapeIdentifier(databaseName) + "` "
                + "CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci";
        Mono.usingWhen(
                        openBootstrapConnection(),
                        connection -> Flux.from(connection.createStatement(sql).execute())
                                .flatMap(result -> result.getRowsUpdated())
                                .then(),
                        Connection::close
                )
                .timeout(operationTimeout)
                .block(operationTimeout);
        logger.info("database {} not exists, created successfully", databaseName);
    }

    private Mono<Connection> openBootstrapConnection() {
        return Mono.from(bootstrapConnectionFactory.create())
                .cast(Connection.class)
                .timeout(connectionTimeout);
    }

    private ConnectionPool createConnectionPool(String url) {
        ConnectionFactory connectionFactory = createConnectionFactory(url);
        ConnectionPoolConfiguration.Builder builder = ConnectionPoolConfiguration.builder(connectionFactory)
                .initialSize(poolInitialSize)
                .maxSize(poolMaxSize)
                .maxIdleTime(poolMaxIdleTime)
                .maxAcquireTime(connectionTimeout)
                .maxCreateConnectionTime(connectionTimeout);
        if (validationQuery != null && !validationQuery.isBlank()) {
            builder.validationQuery(validationQuery);
        }
        return new ConnectionPool(builder.build());
    }

    private ConnectionFactory createConnectionFactory(String url) {
        ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions.parse(url).mutate();
        builder.option(MySqlConnectionFactoryProvider.LOOP_RESOURCES, loopResources);
        if (username != null && !username.isBlank()) {
            builder.option(USER, username);
        }
        if (password != null && !password.isBlank()) {
            builder.option(PASSWORD, password);
        }
        return ConnectionFactories.get(builder.build());
    }

    private String requireUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new SysException("db mysql r2dbc url is empty");
        }
        return url;
    }

    private String stripDatabase(String url) {
        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) {
            throw new SysException("invalid r2dbc url: " + url);
        }
        int pathIdx = url.indexOf('/', schemeIdx + 3);
        if (pathIdx < 0) {
            return url.endsWith("/") ? url : url + "/";
        }
        int queryIdx = url.indexOf('?', pathIdx);
        String prefix = url.substring(0, pathIdx + 1);
        return queryIdx < 0 ? prefix : prefix + url.substring(queryIdx);
    }

    private String escapeIdentifier(String identifier) {
        return identifier.replace("`", "``");
    }

    @Override
    public void close() {
        serialExecutor.close(() -> {
            connectionPool.dispose();
            loopResources.dispose();
        });
    }

    @Override
    public void dropTables(String[] tableNames) throws Exception {
    }
}
