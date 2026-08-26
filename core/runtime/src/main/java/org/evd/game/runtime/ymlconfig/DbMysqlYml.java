package org.evd.game.runtime.ymlconfig;

public class DbMysqlYml {
    private String jdbcUrl;
    private String r2dbcUrl;
    private String driverClass = "com.mysql.cj.jdbc.Driver";
    private String username;
    private String password;
    private String database;
    private boolean autoCreate = true;
    private int connectionTimeoutMs = 4000;
    private int operationTimeoutMs = 10000;
    private int poolInitialSize = 4;
    private int poolMaxSize = 16;
    private int poolMaxIdleTimeMs = 300000;
    private String testQuery = "SELECT 1";

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getR2dbcUrl() {
        return r2dbcUrl;
    }

    public void setR2dbcUrl(String r2dbcUrl) {
        this.r2dbcUrl = r2dbcUrl;
    }

    public String getResolvedR2dbcUrl() {
        if (r2dbcUrl != null && !r2dbcUrl.isBlank()) {
            return r2dbcUrl;
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        if (jdbcUrl.startsWith("jdbc:mysql://")) {
            return "r2dbc:mysql://" + jdbcUrl.substring("jdbc:mysql://".length());
        }
        throw new IllegalArgumentException("unsupported mysql url: " + jdbcUrl);
    }

    public String getResolvedJdbcUrl() {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        if (r2dbcUrl == null || r2dbcUrl.isBlank()) {
            return null;
        }
        if (r2dbcUrl.startsWith("r2dbc:mysql://")) {
            return "jdbc:mysql://" + r2dbcUrl.substring("r2dbc:mysql://".length());
        }
        throw new IllegalArgumentException("unsupported mysql url: " + r2dbcUrl);
    }

    public String getDriverClass() {
        return driverClass;
    }

    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public int getOperationTimeoutMs() {
        return operationTimeoutMs;
    }

    public void setOperationTimeoutMs(int operationTimeoutMs) {
        this.operationTimeoutMs = operationTimeoutMs;
    }

    public int getPoolInitialSize() {
        return poolInitialSize;
    }

    public void setPoolInitialSize(int poolInitialSize) {
        this.poolInitialSize = poolInitialSize;
    }

    public int getPoolMaxSize() {
        return poolMaxSize;
    }

    public void setPoolMaxSize(int poolMaxSize) {
        this.poolMaxSize = poolMaxSize;
    }

    public int getPoolMaxIdleTimeMs() {
        return poolMaxIdleTimeMs;
    }

    public void setPoolMaxIdleTimeMs(int poolMaxIdleTimeMs) {
        this.poolMaxIdleTimeMs = poolMaxIdleTimeMs;
    }

    public String getTestQuery() {
        return testQuery;
    }

    public void setTestQuery(String testQuery) {
        this.testQuery = testQuery;
    }
}
