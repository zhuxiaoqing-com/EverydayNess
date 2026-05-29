package org.evd.game.runtime.config;

public class DbServiceConfig {
    private String engine = "mysql";
    private DbMysqlConfig mysql = new DbMysqlConfig();
    private DbMongoConfig mongo = new DbMongoConfig();
    private DbStorageConfig storage = new DbStorageConfig();

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public DbMysqlConfig getMysql() {
        return mysql;
    }

    public void setMysql(DbMysqlConfig mysql) {
        this.mysql = mysql;
    }

    public DbMongoConfig getMongo() {
        return mongo;
    }

    public void setMongo(DbMongoConfig mongo) {
        this.mongo = mongo;
    }

    public DbStorageConfig getStorage() {
        return storage;
    }

    public void setStorage(DbStorageConfig storage) {
        this.storage = storage;
    }
}
