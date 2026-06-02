package org.evd.game.gencode.db;

final class DbDirtyStorageTableRenderer {
    private final DbDirtyMysqlTableRenderer mysqlTableRenderer = new DbDirtyMysqlTableRenderer();
    private final DbDirtyJsonTableRenderer jsonTableRenderer = new DbDirtyJsonTableRenderer();
    private final DbDirtyPbTableRenderer pbTableRenderer = new DbDirtyPbTableRenderer();

    String render(DbDirtyEntityMeta entity) {
        return switch (entity.dbType) {
            case MYSQL -> mysqlTableRenderer.render(entity);
            case JSON -> jsonTableRenderer.render(entity);
            case PB -> pbTableRenderer.render(entity);
        };
    }
}
