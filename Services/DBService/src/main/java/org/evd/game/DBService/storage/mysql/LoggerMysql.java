package org.evd.game.DBService.storage.mysql;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.evd.game.runtime.config.DbMysqlConfig;
import org.evd.game.runtime.support.SysException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

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
    private final int poolInitialSize;
    private final int poolMaxSize;
    private final Duration poolMaxIdleTime;
    private final ConnectionFactory bootstrapConnectionFactory;
    private final ConnectionPool connectionPool;

    public LoggerMysql(DbMysqlConfig config) {
        try {
            this.autoCreate = config.isAutoCreate();
            this.database = config.getDatabase();
            this.username = config.getUsername();
            this.password = config.getPassword();
            this.validationQuery = config.getTestQuery();
            this.connectionTimeout = Duration.ofMillis(config.getConnectionTimeoutMs());
            this.operationTimeout = Duration.ofMillis(config.getOperationTimeoutMs());
            this.poolInitialSize = config.getPoolInitialSize();
            this.poolMaxSize = config.getPoolMaxSize();
            this.poolMaxIdleTime = Duration.ofMillis(config.getPoolMaxIdleTimeMs());
            this.databaseUrl = requireUrl(config.getResolvedR2dbcUrl());
            this.bootstrapUrl = autoCreate ? stripDatabase(databaseUrl) : databaseUrl;
            this.bootstrapConnectionFactory = createConnectionFactory(bootstrapUrl);
            checkDatabase(database);
            this.connectionPool = createConnectionPool(databaseUrl);
            logger.info("MySQL R2DBC pool init OK, autoCreate={}, initialSize={}, maxSize={}",
                    autoCreate, poolInitialSize, poolMaxSize);
        } catch (Exception e) {
            logger.error("LoggerMysql error", e);
            Runtime.getRuntime().halt(0);
            throw new SysException(e);
        }
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public Duration getOperationTimeout() {
        return operationTimeout;
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
        connectionPool.dispose();
    }

    @Override
    public void dropTables(String[] tableNames) throws Exception {
    }
}
