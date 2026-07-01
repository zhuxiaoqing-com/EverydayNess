package org.evd.game.DBService.storage.mysql;

import com.mongodb.client.MongoDatabase;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mongodb實現
 *
 * @author Eric.Ma
 *
 */
public class StorageMongo implements StorageEngine {

    private static final Logger log = LoggerFactory.getLogger(StorageMongo.class);

    private LoggerMongo logger;


    private static final String KEY = "key";

    private static final String VALUE = "value";

    private MongoDatabase database;

    public StorageMongo(LoggerMongo logger) {
        this.logger = logger;
    }

    @Override
    public DBRsp find(DBReq _dbReq) {
        return unsupported();
    }

    @Override
    public boolean insert(DBReq _dbReq) {
        return unsupported();
    }

    @Override
    public void replace(DBReq _dbReq) {
        unsupported();
    }

    @Override
    public void replaceBatch(DBReq _dbReq) {
        unsupported();
    }

    @Override
    public void remove(DBReq _dbReq) {
        unsupported();
    }

    @Override
    public void removeBatch(DBReq _dbReq) {
        unsupported();
    }

    @Override
    public DBRsp findBatch(DBReq _dbReq) {
        return unsupported();
    }

    @Override
    public boolean detect() {
        return unsupported();
    }

    @Override
    public void initTable(DBReq _dbReq) {
        unsupported();
    }

    @Override
    public void close() {

    }

    private <T> T unsupported() {
        throw new UnsupportedOperationException("StorageMongo is not implemented");
    }

	/*	@Override
	public void initTable(String tableName) {
		// 創建集合
		database = this.logger.getMongoClient().getDatabase(logger.getDataBase());
		boolean firstCreate = false;
		if (!database.listCollectionNames().into(new ArrayList<>()).contains(tableName)) {
			database.createCollection(tableName);
			firstCreate = true;
		}
		MongoCollection<DBObject> table = database.getCollection(tableName, DBObject.class);
		if (firstCreate) {
			BasicDBObject index = new BasicDBObject("key", 1);
			table.createIndex(index, new IndexOptions().unique(true).name("key_index"));
		}
	}

	private MongoCollection<DBObject> getTable(String tableName) {
		return database.getCollection(tableName, DBObject.class);
	}

	@Override
	public String find(String tableName, String key) {
		long begin = System.nanoTime();
		try {
			MongoCollection<DBObject> table = getTable(tableName);
			BasicDBObject query = new BasicDBObject(KEY, key);
			FindIterable<DBObject> cursor = table.find(query);
			DBObject doc = cursor.first();
			if (doc == null)
				return null;
			Object value = doc.get(VALUE);
			return JSON.serialize(value);
		} finally {
			log.debug("{} find key : {}, cost : {} ms", tableName, key, (System.nanoTime() - begin) * 1e-6);
		}
	}

	@Override
	public boolean exist(String tableName, String key) {
		long begin = System.nanoTime();
		try {
			MongoCollection<DBObject> table = getTable(tableName);
			BasicDBObject query = new BasicDBObject(KEY, key);
			FindIterable<DBObject> cursor = table.find(query);
			DBObject doc = cursor.first();
			return doc != null;
		} finally {
			log.debug("{} exist key : {} cost : {} ms", tableName, key, (System.nanoTime() - begin) * 1e-6);
		}
	}

	@Override
	public boolean insert(String tableName, String key, String value) {
		long begin = System.nanoTime();
		try {
			MongoCollection<DBObject> table = getTable(tableName);
			BasicDBObject doc = new BasicDBObject();
			doc.append(KEY, key).append(VALUE, JSON.parse(value));
			table.insertOne(doc);
			return true;
		} finally {
			log.debug("{} insert key : {} cost: {} ms", tableName, key, (System.nanoTime() - begin) * 1e-6);
		}
	}

	@Override
	public void replace(String tableName, String key, String value) {
		long begin = System.nanoTime();
		try {
			MongoCollection<DBObject> table = getTable(tableName);
			BasicDBObject keyQuery = new BasicDBObject(KEY, key);
			BasicDBObject doc = new BasicDBObject();
			doc.append(KEY, key);
			Object obj = JSON.parse(value);
			doc.append(VALUE, obj);
			table.replaceOne(keyQuery, doc, new UpdateOptions().upsert(true));
		} finally {
			log.debug("{} replace key : {} value: {} cost : {} ms", tableName, key, value,
					(System.nanoTime() - begin) * 1e-6);
		}
	}

	@Override
	public void remove(String tableName, String key) {
		long begin = System.nanoTime();
		try {
			MongoCollection<DBObject> table = getTable(tableName);
			BasicDBObject searchQuery = new BasicDBObject();
			searchQuery.put(KEY, key);
			table.deleteOne(searchQuery);
		} finally {
			log.debug("{} remove key : {} cost : {} ms", tableName, key, (System.nanoTime() - begin) * 1e-6);
		}
	}

	@Override
	public boolean detect() {
		try {
			if (database == null) {
				return false;
			}
			// 执行 ping 命令并检查响应
            // execute ping command and check response
            Document pingResult = database.runCommand(new Document("ping", 1));
			return pingResult.getDouble("ok") == 1.0;
		} catch (Exception e) {
			// 连接失败时捕获异常
			log.error("", e);
			return false;
		}
	}

	@Override
	public void close() {
		logger.close();
	}

	public static void main(String[] args) {
		Object obj = JSON.parse("");
		System.err.println(obj);
	}*/

}
