package org.evd.game.DBService.storage.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.evd.game.runtime.config.DbMysqlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.evd.game.runtime.support.SysException;

import java.sql.*;

public class LoggerMysql implements LoggerEngine {

	private static Logger logger = LoggerFactory.getLogger(LoggerMysql.class);

	private final String databaseUrl;
    private final String bootstrapUrl;
	private String database;
	private boolean autoCreate;
	private HikariDataSource readConnection = null;
	private HikariDataSource writeConnection = null;
    private final String driverClass;
    private final String username;
    private final String password;
    private final int connectionTimeoutMs;
    private final String testQuery;

	public LoggerMysql(DbMysqlConfig config) {
		try {
			this.autoCreate = config.isAutoCreate();
			this.database = config.getDatabase();
            this.driverClass = config.getDriverClass();
            this.username = config.getUsername();
            this.password = config.getPassword();
			this.connectionTimeoutMs = config.getConnectionTimeoutMs();
			this.testQuery = config.getTestQuery();
            this.databaseUrl = config.getJdbcUrl();
			if(autoCreate) {
				this.bootstrapUrl = config.getJdbcUrl().replaceAll(database, "");
			}else {
				this.bootstrapUrl = config.getJdbcUrl();
			}
            initConnections(config.getMinPoolSize(), config.getMaxPoolSize(), bootstrapUrl);
			checkDatabase(database);
            if (autoCreate) {
                resetConnections(config.getMinPoolSize(), config.getMaxPoolSize(), databaseUrl);
            }
			if (readConnection == null || writeConnection == null) {
				logger.error("MySQL Connection Pool Init Error, System Exit!");
				Runtime.getRuntime().halt(0);
			} else {
				logger.info("MySQL Connection Pool Init OK!");
			}
		} catch (Exception e) {
			logger.error("LoggerMysql error", e);
			Runtime.getRuntime().halt(0);
			throw new SysException(e);
		}
	}

	public boolean isAutoCreate() {
		return autoCreate;
	}

	private void checkDatabase(String dataBase) {
		Connection conn = null;
		PreparedStatement stat = null;
		ResultSet rs = null;
		try {
			conn = writeConnection.getConnection();
			stat = conn.prepareStatement("show databases like ?");
			stat.setString(1, dataBase);
			rs = stat.executeQuery();
			if (!rs.next()) {
				if(autoCreate) {
					try (Statement st = conn.createStatement()) {
						//st.execute("create database " + dataBase);
						st.execute(
								"CREATE DATABASE IF NOT EXISTS `" + dataBase + "` " +
										"CHARACTER SET utf8mb4 " +
										"COLLATE utf8mb4_general_ci"
						);
						logger.info("database {} not exsits, created successfully", dataBase);
					}
				}else {
					throw new SysException("database is not inited, please execute initDB.sql into mysql.");
				}
			}

          /*  try (Statement st = conn.createStatement()) {
                st.execute("USE " + dataBase);
            }*/

		} catch (SQLException e) {
			throw new SysException(e);
		} finally {
			release(rs, stat, conn);
		}
	}

	public String getTableName(String tableName) {
		return tableName;
	}

	private HikariConfig InitHikariConfig(String poolName, String url, int minPool, int maxPool) {
		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setPoolName(poolName);
		hikariConfig.setDriverClassName(driverClass);
		hikariConfig.setJdbcUrl(url);
        if (username != null && !username.isBlank()) {
            hikariConfig.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            hikariConfig.setPassword(password);
        }
		hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
		hikariConfig.addDataSourceProperty("prepStmtCacheSize", 512);
		hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
		hikariConfig.setConnectionTimeout(connectionTimeoutMs);
		hikariConfig.setConnectionTestQuery(testQuery);
		hikariConfig.setAutoCommit(true);
		hikariConfig.setMinimumIdle(minPool);
		hikariConfig.setMaximumPoolSize(maxPool);
		return hikariConfig;
	}

    private void initConnections(int minPool, int maxPool, String jdbcUrl) {
        this.writeConnection = new HikariDataSource(InitHikariConfig("mdb-write-pool", jdbcUrl, minPool, maxPool));
        this.readConnection = new HikariDataSource(InitHikariConfig("mdb-read-pool", jdbcUrl, minPool, maxPool));
    }

    private void resetConnections(int minPool, int maxPool, String jdbcUrl) {
        close();
        initConnections(minPool, maxPool, jdbcUrl);
    }

	@Override
	public void close() {
		try {
            if (writeConnection != null) {
			    writeConnection.close();
            }
            if (readConnection != null) {
			    readConnection.close();
            }
		} catch (Exception e) {
			logger.error("close connection", e);
		}
	}

	@Override
	public void dropTables(String[] tableNames) throws Exception {
	}

	public Connection getReadConnection() throws SQLException {
		return readConnection.getConnection();
	}

	public Connection getWriteConnection() throws SQLException {
		return writeConnection.getConnection();
	}

	public static void release(AutoCloseable... res) {
		try {
			for (AutoCloseable ac : res) {
				if (ac != null)
					ac.close();
			}
		} catch (Exception e) {
		}
	}
}
