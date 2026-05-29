package org.evd.game.DBService.storage.mysql;

public interface LoggerEngine {
	
	void dropTables(String[] tableNames) throws Exception;

	void close();
}
