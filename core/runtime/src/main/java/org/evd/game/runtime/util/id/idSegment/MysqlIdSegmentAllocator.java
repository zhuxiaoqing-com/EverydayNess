package org.evd.game.runtime.util.id.idSegment;

import org.evd.game.runtime.ymlconfig.DbYml;
import org.evd.game.runtime.ymlconfig.DbMysqlYml;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.util.id.IDEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;

/** 只负责通过 MySQL JDBC 申请一个 ID 号段。 */
final class MysqlIdSegmentAllocator extends IdSegmentAllocator {

    private static final Logger log = LoggerFactory.getLogger(MysqlIdSegmentAllocator.class);
    private static final String SEGMENT_TABLE = "evd_id_segment";
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS `" + SEGMENT_TABLE + "` ("
            + "`id_type` INT NOT NULL PRIMARY KEY, "
            + "`next_id` BIGINT NOT NULL"
            + ")";

    private final Object connectionLock = new Object();
    private volatile Connection connection;
    private volatile Duration connectionTimeout = Duration.ofSeconds(4);
    private volatile Duration operationTimeout = Duration.ofSeconds(10);

    MysqlIdSegmentAllocator() {
        initializeStorage();
    }

    /** 通过 MySQL 原子申请一个号段，范围策略由布局层决定。 */
    @Override
    protected long doReserveStart(IDEnum idEnum, int segmentSize, long maxIncrementId) {
        int id = idEnum.getId();
        synchronized (connectionLock) {
            Connection actualConnection = ensureConnection();
            long operationDeadlineNanos = operationDeadlineNanos();
            try (PreparedStatement statement = actualConnection.prepareStatement("""
                        UPDATE `%s`
                        SET `next_id` = LAST_INSERT_ID(`next_id` + ?)
                        WHERE `id_type` = ?
                          AND `next_id` <= ?
                        """.formatted(SEGMENT_TABLE), Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, segmentSize);
                statement.setInt(2, id);
                statement.setLong(3, maxIncrementId - segmentSize + 1);
                statement.setQueryTimeout(timeoutSeconds(operationDeadlineNanos));

                int affected = statement.executeUpdate();
                if (affected == 0) {
                    throw new IllegalStateException(
                            "mysql id increment overflow or id_type not exists: idEnum=" + idEnum);
                }

                try (ResultSet resultSet = statement.getGeneratedKeys()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("mysql id increment returned no id");
                    }
                    return resultSet.getLong(1) - segmentSize;
                }
            } catch (SQLException | RuntimeException | Error e) {
                throw asRuntimeException("mysql id segment allocation failed", e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (connectionLock) {
            Connection actualConnection = connection;
            connection = null;
            if (actualConnection != null) {
                try {
                    actualConnection.close();
                } catch (SQLException e) {
                    throw asRuntimeException("mysql id segment connection close failed", e);
                }
            }
        }
    }

    private Connection ensureConnection() {
        Connection actualConnection = connection;
        if (actualConnection != null) {
            try {
                if (!actualConnection.isClosed()
                        && actualConnection.isValid(timeoutSeconds(connectionTimeout))) {
                    return actualConnection;
                }
            } catch (SQLException e) {
                log.warn("MySQL ID 号段连接健康检查失败，将重新连接", e);
            }
            invalidateConnection(actualConnection, "连接已关闭或健康检查失败");
        }

        DbYml dbConfig = GlobalYml.requireDbConfig();
        if (!"mysql".equalsIgnoreCase(dbConfig.getDb().getEngine())) {
            throw new IllegalStateException("mysql id layout requires mysql db engine: "
                    + dbConfig.getDb().getEngine());
        }
        DbMysqlYml mysqlConfig = dbConfig.getDb().getMysql();
        String url = mysqlConfig.getResolvedJdbcUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("mysql id layout requires db.mysql.jdbcUrl or r2dbcUrl");
        }
        operationTimeout = Duration.ofMillis(mysqlConfig.getOperationTimeoutMs());
        connectionTimeout = Duration.ofMillis(mysqlConfig.getConnectionTimeoutMs());

        Connection created = null;
        try {
            Class.forName(mysqlConfig.getDriverClass());
            Properties properties = new Properties();
            if (mysqlConfig.getUsername() != null) {
                properties.setProperty("user", mysqlConfig.getUsername());
            }
            if (mysqlConfig.getPassword() != null) {
                properties.setProperty("password", mysqlConfig.getPassword());
            }
            properties.setProperty("connectTimeout", String.valueOf(connectionTimeout.toMillis()));
            properties.setProperty("socketTimeout", String.valueOf(operationTimeout.toMillis()));
            created = DriverManager.getConnection(url, properties);
            connection = created;
            return created;
        } catch (ClassNotFoundException | SQLException | RuntimeException e) {
            if (created != null) {
                try {
                    created.close();
                } catch (SQLException closeError) {
                    e.addSuppressed(closeError);
                }
            }
            throw asRuntimeException("mysql id segment connection failed", e);
        }
    }

    private void initializeStorage() {
        synchronized (connectionLock) {
            Connection actualConnection = ensureConnection();
            boolean transactionStarted = false;
            long operationDeadlineNanos = operationDeadlineNanos();
            try {
                actualConnection.setAutoCommit(false);
                transactionStarted = true;
                executeUpdate(actualConnection, CREATE_TABLE_SQL, operationDeadlineNanos);
                for (IDEnum idEnum : IDEnum.values()) {
                    executeUpdate(actualConnection, "INSERT IGNORE INTO `" + SEGMENT_TABLE
                            + "` (`id_type`, `next_id`) VALUES (?, ?)",
                            operationDeadlineNanos, idEnum.getId(), 0L);
                }
                actualConnection.commit();
                transactionStarted = false;
                actualConnection.setAutoCommit(true);
            } catch (SQLException | RuntimeException | Error e) {
                if (transactionStarted) {
                    rollback(actualConnection);
                }
                throw asRuntimeException("mysql id segment storage initialization failed", e);
            }
        }
    }

    private int executeUpdate(Connection actualConnection, String sql, long deadlineNanos,
                              Object... values) throws SQLException {
        try (PreparedStatement statement = actualConnection.prepareStatement(sql)) {
            bind(statement, values);
            statement.setQueryTimeout(timeoutSeconds(deadlineNanos));
            return statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i]);
        }
    }

    private void rollback(Connection actualConnection) {
        try {
            actualConnection.rollback();
            actualConnection.setAutoCommit(true);
        } catch (SQLException rollbackError) {
            invalidateConnection(actualConnection, "事务回滚失败");
            log.error("MySQL ID 号段事务回滚失败", rollbackError);
        }
    }

    private void invalidateConnection(Connection actualConnection, String reason) {
        if (connection == actualConnection) {
            connection = null;
        }
        try {
            if (!actualConnection.isClosed()) {
                actualConnection.close();
            }
        } catch (SQLException e) {
            log.warn("MySQL ID 号段关闭失效连接失败: reason={}", reason, e);
        }
        log.warn("MySQL ID 号段连接已失效: reason={}", reason);
    }

    private long operationDeadlineNanos() {
        return System.nanoTime() + operationTimeout.toNanos();
    }

    private int timeoutSeconds(Duration timeout) {
        return Math.max(1, (int) Math.ceil(timeout.toMillis() / 1000.0));
    }

    private int timeoutSeconds(long deadlineNanos) throws SQLTimeoutException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new SQLTimeoutException("mysql id segment operation timeout");
        }
        return Math.max(1, (int) Math.ceil(remainingNanos / 1_000_000_000.0));
    }

    private RuntimeException asRuntimeException(String message, Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(message, cause);
    }

}
