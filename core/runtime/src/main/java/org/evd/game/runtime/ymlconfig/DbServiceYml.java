package org.evd.game.runtime.ymlconfig;

public class DbServiceYml {
    private String engine = "mysql";
    private DbSnapshotStoreYml snapshotStore = new DbSnapshotStoreYml();
    private DbMysqlYml mysql = new DbMysqlYml();
    private DbMongoYml mongo = new DbMongoYml();
    private DbStorageYml storage = new DbStorageYml();

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public DbSnapshotStoreYml getSnapshotStore() {
        return snapshotStore;
    }

    public void setSnapshotStore(DbSnapshotStoreYml snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    public DbMysqlYml getMysql() {
        return mysql;
    }

    public void setMysql(DbMysqlYml mysql) {
        this.mysql = mysql;
    }

    public DbMongoYml getMongo() {
        return mongo;
    }

    public void setMongo(DbMongoYml mongo) {
        this.mongo = mongo;
    }

    public DbStorageYml getStorage() {
        return storage;
    }

    public void setStorage(DbStorageYml storage) {
        this.storage = storage;
    }
}
