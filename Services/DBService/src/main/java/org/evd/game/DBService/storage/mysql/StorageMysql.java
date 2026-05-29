package org.evd.game.DBService.storage.mysql;

import org.evd.game.DbEntity.serialize.*;
import org.evd.game.runtime.config.DbStorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.evd.game.runtime.support.SysException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class StorageMysql implements StorageEngine {
	private final int batchPerCount;
	private final int costMsWarn;
	private final int batchCostMsWarn;

	private static final Logger log = LoggerFactory.getLogger(StorageMysql.class);

	private final LoggerMysql logger;



    public StorageMysql(LoggerMysql logger, DbStorageConfig storageConfig) {
		this.logger = logger;
		this.batchPerCount = storageConfig.getBatchPerCount();
		this.costMsWarn = storageConfig.getCostMsWarn();
		this.batchCostMsWarn = storageConfig.getBatchCostMsWarn();
    }

	/**
	 init 直接生成整个sql
	 CREATE TABLE IF NOT EXISTS " + dataBaseTableName
	 + "(k VARCHAR(128) NOT NULL PRIMARY KEY, v JSON NOT NULL) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI

	 replace sql + ??
	 REPLACE INTO " + dataBaseTableName + " VALUES(?, ?)

	 replaceBatch sql + ?? 批量 ??
	 REPLACE INTO " + dataBaseTableName + " VALUES(?, ?)

	 removeBatch sql + 批量 ??
	 DELETE FROM " + dataBaseTableName + " WHERE k = ?

	 insert
	 INSERT INTO " + dataBaseTableName + " VALUES(?, ?)


	 remove
	 DELETE FROM " + dataBaseTableName + " WHERE k = ?

	 find
	 SELECT v FROM " + dataBaseTableName + " WHERE k = ?

	 findBatch
	 SELECT * FROM player WHERE player_id IN (?, ?, ?, ?);
	 */



	@Override
	public void initTable(DBReq _dbReq) {
		Connection conn = null;
		PreparedStatement stat = null;
		ResultSet rs = null;
		MysqlReq mysqlReq = _dbReq.getMysqlReq();
		try {
			conn = logger.getWriteConnection();
			if(logger.isAutoCreate()) {
				stat = conn.prepareStatement(mysqlReq.getSql());
				stat.execute();
			}else {
				stat = conn.prepareStatement("SHOW TABLES LIKE ?");
				stat.setString(1, mysqlReq.getTableName());
				rs = stat.executeQuery();
				if (!rs.next()) {
					throw new SysException("table is not inited, please execute initTable.sql into mysql.");
				}
			}

		} catch (Exception e) {
			log.error("init error, table={}", mysqlReq.getTableName(), e);
			throw new SysException(e);
		} finally {
			LoggerMysql.release(stat, conn);
		}
	}

	@Override
	public void replace(DBReq _dbReq) {
		MysqlReq mysqlReq = _dbReq.getMysqlReq();

		long begin = System.nanoTime();
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
			conn = logger.getWriteConnection();
			stmt = conn.prepareStatement(mysqlReq.getSql());
			List<DbValue> valueList = mysqlReq.getSingleTableField().getValueList();
			for (int i = 0; i < valueList.size(); i++) {
				DbValue dbValue = valueList.get(i);
				stmt.setObject(i+1, dbValue.getV());
			}
			stmt.executeUpdate();
		} catch (Exception e) {
			log.error("replace error, table={}, key={}", mysqlReq.getTableName(), mysqlReq.getSingleTableKey(), e);
			throw new SysException(e);
		} finally {
			LoggerMysql.release(stmt, conn);

			long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
			if (costMs > costMsWarn) {
				log.warn("table {} replace key : {}  cost: {} ms", mysqlReq.getTableName(), mysqlReq.getSingleTableKey(),
						costMs);
			}
		}
	}

	@Override
	public void replaceBatch(DBReq _dbReq) {
		MysqlReq mysqlReq = _dbReq.getMysqlReq();

		long begin = System.nanoTime();
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
			conn = logger.getWriteConnection();
			stmt = conn.prepareStatement(mysqlReq.getSql());
			int idx = 0;
			for (DbTableField entry : mysqlReq.getTablFieldList()) {
				List<DbValue> valueList = entry.getValueList();
				for (int i = 0; i < valueList.size(); i++) {
					DbValue dbValue = valueList.get(i);
					stmt.setObject(i+1, dbValue.getV());
				}
				stmt.addBatch();
				idx++;
				if (idx == batchPerCount) {
					stmt.executeBatch();
					stmt.clearBatch();
					idx = 0;
				}
			}
			if (idx != 0) {
				stmt.executeBatch();
				stmt.clearBatch();
			}

		} catch (Exception e) {
			log.error("replace batch error, table={}, keys={}, num={}",
					mysqlReq.getTableName(), getBatchKeys(mysqlReq), mysqlReq.getTablFieldList().size(), e);
			throw new SysException(e);
		} finally {
			LoggerMysql.release(stmt, conn);
			long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
			if (costMs > batchCostMsWarn) {
				log.warn("table {} num {} batch replace cost: {} ms",
						mysqlReq.getTableName(), mysqlReq.getTablFieldList().size(), (System.nanoTime() - begin) * 1e-6);
			}
		}
	}

	@Override
	public void removeBatch(DBReq _dbReq) {
		MysqlReq mysqlReq = _dbReq.getMysqlReq();
		long begin = System.nanoTime();
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
			conn = logger.getWriteConnection();
			stmt = conn.prepareStatement(mysqlReq.getSql());
			int idx = 0;
			for (DbTableField entry : mysqlReq.getTablFieldList()) {
				List<DbValue> valueList = entry.getValueList();
				for (int i = 0; i < valueList.size(); i++) {
					DbValue dbValue = valueList.get(i);
					stmt.setObject(i + 1, dbValue.getV());
				}
				stmt.addBatch();
				idx++;
				if (idx == batchPerCount) {
					stmt.executeBatch();
					stmt.clearBatch();
					idx = 0;
				}
			}
			if (idx != 0) {
				stmt.executeBatch();
				stmt.clearBatch();
			}

		} catch (Exception e) {
			log.error("remove batch error, table={}, keys={}, num={}",
					mysqlReq.getTableName(), getBatchKeys(mysqlReq), mysqlReq.getTablFieldList().size(), e);
			throw new SysException(e);
		} finally {
			LoggerMysql.release(stmt, conn);

			long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
			if (costMs > batchCostMsWarn) {
				log.warn("table {} num {} batch remove cost: {} ms",
						mysqlReq.getTableName(), mysqlReq.getTablFieldList().size(), costMs);
			}
		}
	}

	@Override
	public boolean insert(DBReq _dbReq) {
		MysqlReq mysqlReq = _dbReq.getMysqlReq();
		long begin = System.nanoTime();
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
			conn = logger.getWriteConnection();
			stmt = conn.prepareStatement(mysqlReq.getSql());
			List<DbValue> valueList = mysqlReq.getSingleTableField().getValueList();
			for (int i = 0; i < valueList.size(); i++) {
				DbValue dbValue = valueList.get(i);
				stmt.setObject(i + 1, dbValue.getV());
			}
			stmt.executeUpdate();
		} catch (Exception e) {
			log.error("insert error, table={}, key={}", mysqlReq.getTableName(), mysqlReq.getSingleTableKey(), e);
			return false;
		} finally {
			LoggerMysql.release(stmt, conn);

			long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
			if (costMs > costMsWarn) {
				log.warn("table {} insert key : {} cost: {} ms",
						mysqlReq.getTableName(), mysqlReq.getSingleTableKey(), costMs);
			}
		}
		return true;
	}

	@Override
	public void remove(DBReq _dbReq) {
		MysqlReq mysqlReq = _dbReq.getMysqlReq();
		long begin = System.nanoTime();
		Connection conn = null;
		PreparedStatement stmt = null;
		try {
			conn = logger.getWriteConnection();
			stmt = conn.prepareStatement(mysqlReq.getSql());
			List<DbValue> valueList = mysqlReq.getSingleTableField().getValueList();
			for (int i = 0; i < valueList.size(); i++) {
				DbValue dbValue = valueList.get(i);
				stmt.setObject(i + 1, dbValue.getV());
			}
			stmt.executeUpdate();
		} catch (Exception e) {
			log.error("remove error, table={}, key={}", mysqlReq.getTableName(), mysqlReq.getSingleTableKey(), e);
			throw new SysException(e);
		} finally {
			LoggerMysql.release(stmt, conn);

			long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
			if (costMs > costMsWarn) {
				log.warn("table {} remove key : {} cost: {} ms",
						mysqlReq.getTableName(), mysqlReq.getSingleTableKey(), costMs);
			}
		}
	}

	@Override
	public DBRsp find(DBReq _dbReq) {
		MysqlReq mysqlReq = _dbReq.getMysqlReq();
		long begin = System.nanoTime();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			conn = logger.getReadConnection();
			stmt = conn.prepareStatement(mysqlReq.getSql());
			List<DbValue> valueList = mysqlReq.getSingleTableField().getValueList();
			for (int i = 0; i < valueList.size(); i++) {
				DbValue dbValue = valueList.get(i);
				stmt.setObject(i + 1, dbValue.getV());
			}
			rs = stmt.executeQuery();
			List<DbValue> resultList = new ArrayList<>();
			if (rs.next()) {
				int columnCount = rs.getMetaData().getColumnCount();
				for (int i = 1; i <= columnCount; i++) {
					resultList.add(new DbValue(rs.getObject(i)));
				}
			}

			MysqlRsp mysqlRsp = new MysqlRsp();
			mysqlRsp.getTablFieldList().add(new DbTableField(resultList));
			DBRsp dbRsp = new DBRsp();
			dbRsp.setMysqlRsp(mysqlRsp);
			return dbRsp;
		} catch (Exception e) {
			log.error("find error, table={}, key={}", mysqlReq.getTableName(), mysqlReq.getSingleTableKey(), e);
			throw new SysException(e);
		} finally {
			LoggerMysql.release(rs, stmt, conn);
			long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
			if (costMs > costMsWarn) {
				log.warn("table {} find key : {} cost: {} ms",
						mysqlReq.getTableName(), mysqlReq.getSingleTableKey(), costMs);
			}
		}
	}

	@Override
	public DBRsp findBatch(DBReq _dbReq) {
		MysqlReq mysqlReq = _dbReq.getMysqlReq();
		long begin = System.nanoTime();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			conn = logger.getReadConnection();
			stmt = conn.prepareStatement(mysqlReq.getSql());
			List<DbValue> valueList = mysqlReq.getSingleTableField().getValueList();
			for (int i = 0; i < valueList.size(); i++) {
				DbValue dbValue = valueList.get(i);
				stmt.setObject(i + 1, dbValue.getV());
			}
			rs = stmt.executeQuery();
			List<DbTableField> resultRows = new ArrayList<>();
			while (rs.next()) {
				int columnCount = rs.getMetaData().getColumnCount();
				List<DbValue> resultList = new ArrayList<>(columnCount);
				for (int i = 1; i <= columnCount; i++) {
					resultList.add(new DbValue(rs.getObject(i)));
				}
				DbTableField row = new DbTableField();
				row.setValueList(resultList);
				resultRows.add(row);
			}

			MysqlRsp mysqlRsp = new MysqlRsp();
			mysqlRsp.setTablFieldList(resultRows);;
			DBRsp dbRsp = new DBRsp();
			dbRsp.setMysqlRsp(mysqlRsp);
			return dbRsp;
		} catch (Exception e) {
			log.error("find batch error, table={}, keys={}", mysqlReq.getTableName(), getFindBatchKeys(mysqlReq), e);
			throw new SysException(e);
		} finally {
			LoggerMysql.release(rs, stmt, conn);
			long costMs = (long) ((System.nanoTime() - begin) * 1e-6);
			if (costMs > batchCostMsWarn) {
				log.warn("table {} batch find keys : {} cost: {} ms",
						mysqlReq.getTableName(), getFindBatchKeys(mysqlReq), costMs);
			}
		}
	}

	@Override
	public boolean detect() {
		Connection writeConnection = null;
		try {
			writeConnection = logger.getWriteConnection();
			if(writeConnection == null) {
				return false;
			}
			return writeConnection.isValid(3000);
		} catch (Exception e) {
			log.error("detect error", e);
			return false;
		} finally {
			LoggerMysql.release(writeConnection);
		}
	}

	@Override
	public void close() {
		logger.close();
	}

	private String getBatchKeys(MysqlReq mysqlReq) {
		if (mysqlReq.getTablFieldList() == null || mysqlReq.getTablFieldList().isEmpty()) {
			return "[]";
		}
		StringBuilder builder = new StringBuilder("[");
		for (int i = 0; i < mysqlReq.getTablFieldList().size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(mysqlReq.getTableKey(mysqlReq.getTablFieldList().get(i)));
		}
		builder.append(']');
		return builder.toString();
	}

	private String getFindBatchKeys(MysqlReq mysqlReq) {
		DbTableField tableField = mysqlReq.getSingleTableField();
		if (tableField == null || tableField.getValueList() == null || tableField.getValueList().isEmpty()) {
			return "[]";
		}
		StringBuilder builder = new StringBuilder("[");
		List<DbValue> valueList = tableField.getValueList();
		for (int i = 0; i < valueList.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(valueList.get(i).getV());
		}
		builder.append(']');
		return builder.toString();
	}



}
