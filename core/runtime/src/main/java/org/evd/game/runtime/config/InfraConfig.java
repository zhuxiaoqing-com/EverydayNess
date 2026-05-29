package org.evd.game.runtime.config;

public class InfraConfig {
    private DbServiceConfig db = new DbServiceConfig();

    public DbServiceConfig getDb() {
        return db;
    }

    public void setDb(DbServiceConfig db) {
        this.db = db;
    }
}
