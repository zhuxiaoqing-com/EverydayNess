package org.evd.game.DBService.entity;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.DBService.DBService;
import org.evd.game.runtime.config.GlobalConfig;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.runtime.serializeBean.TickTimer;
import org.evd.game.runtime.util.TimeUtils;
import org.evd.game.runtime.serializeBean.TimeoutFlag;
import org.evd.game.runtime.config.DbConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhuxiaoqing
 * @Description: DBCache
 * @Date 2026/7/1 16:13
 **/
@Slf4j
public class DBCache {
    DBService dbService;

    TickTimer tickTimer;
    // 有数据代表在保存中;相当于带超时的标记位
    TimeoutFlag syncSaveFlag;

    Map<String, TableCache> cache = new HashMap<>();

    public DBCache(DBService dbService) {
        DbConfig dbConfig = GlobalConfig.requireDbConfig();
        long millis = dbConfig.getDb().getStorage().getCacheFlushMs();
        this.tickTimer = new TickTimer(millis);
        this.dbService = dbService;
    }

    public void tick() {
        if (!tickTimer.isPeriod(dbService.getTimeCurrent())) {
            return;
        }

        if (!TimeoutFlag.checkExpire(syncSaveFlag)) {
            return;
        }
        dbService.postCoroutine(this::tickCoroutine);
    }

    public void stop() {
        tickCoroutine();
    }

    /**
     * 每个应该需要一个类似版本号的东西，保存之间将版本号获取过来，统一保存完毕后，检查下 符合的全部删除
     */
    private void tickCoroutine() {
        // 时间到了 开始保存;
        syncSaveFlag = new TimeoutFlag(TimeUtils.MIN * 4);
        for (TableCache value : cache.values()) {
            try {
                HashMap<Object, TRecord> copyMap = new HashMap<>(value.getCache());
                List<DBReq> dbReqs = dbService.storageEngine.buildSaveDBReq(value);
                for (DBReq dbReq : dbReqs) {
                    save(dbReq);
                }

                for (Map.Entry<Object, TRecord> entry : copyMap.entrySet()) {
                    TRecord tRecord = value.getCache().get(entry.getKey());
                    // 正常不可能删除的
                    if (tRecord == null) {
                        log.error("tick tRecord == null tableName {} key {}", value.getTableName(), entry.getKey());
                        continue;
                    }

                    // 变化了 代表数据变了
                    if (entry.getValue().tickVersion() != tRecord.tickVersion()) {
                        continue;
                    }

                    // 删除没变化的,因为已经缓存到数据库了
                    value.getCache().remove(entry.getKey());
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }

        syncSaveFlag = null;
    }


    public void save(DBReq dbReq) {
        switch (dbReq.getDbOpType()) {
            case DbOpType.SAVE:
                dbService.storageEngine.replace(dbReq);
                break;
            case DbOpType.BATCH_SAVE:
                dbService.storageEngine.replaceBatch(dbReq);
                break;
            case DbOpType.REMOVE:
                dbService.storageEngine.remove(dbReq);
                break;
            case DbOpType.BATCH_REMOVE:
                dbService.storageEngine.removeBatch(dbReq);
                break;
        }
    }


    public TableCache getTableCache(String tableName) {
        return cache.computeIfAbsent(tableName, a -> new TableCache(tableName));
    }
}
