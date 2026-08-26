package org.evd.game.runtime.ymlconfig;

public class DbYml {
    private DbServiceYml db = new DbServiceYml();

    public DbServiceYml getDb() {
        return db;
    }

    public void setDb(DbServiceYml db) {
        this.db = db;
    }
}
