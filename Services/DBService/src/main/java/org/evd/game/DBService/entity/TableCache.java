package org.evd.game.DBService.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhuxiaoqing
 * @Description: TableCache
 * @Date 2026/7/1 15:36
 **/
public class TableCache {
    public String tableName;
   public  Map<Object, TRecord> cache = new HashMap<>();

    public TableCache(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Map<Object, TRecord> getCache() {
        return cache;
    }

    public void setCache(Map<Object, TRecord> cache) {
        this.cache = cache;
    }
}
