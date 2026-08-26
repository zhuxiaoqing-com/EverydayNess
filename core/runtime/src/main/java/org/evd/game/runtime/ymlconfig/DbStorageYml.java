package org.evd.game.runtime.ymlconfig;

public class DbStorageYml {
    private int batchThreshold = 30;
    private int batchPerCount = 500;
    private int costMsWarn = 100;
    private int batchCostMsWarn = 30000;
    private boolean enableMemoryCache = false;
    private long cacheFlushMs = 60_000L;

    public int getBatchThreshold() {
        return batchThreshold;
    }

    public void setBatchThreshold(int batchThreshold) {
        this.batchThreshold = batchThreshold;
    }

    public int getBatchPerCount() {
        return batchPerCount;
    }

    public void setBatchPerCount(int batchPerCount) {
        this.batchPerCount = batchPerCount;
    }

    public int getCostMsWarn() {
        return costMsWarn;
    }

    public void setCostMsWarn(int costMsWarn) {
        this.costMsWarn = costMsWarn;
    }

    public int getBatchCostMsWarn() {
        return batchCostMsWarn;
    }

    public void setBatchCostMsWarn(int batchCostMsWarn) {
        this.batchCostMsWarn = batchCostMsWarn;
    }

    public boolean isEnableMemoryCache() {
        return enableMemoryCache;
    }

    public void setEnableMemoryCache(boolean enableMemoryCache) {
        this.enableMemoryCache = enableMemoryCache;
    }

    public long getCacheFlushMs() {
        return cacheFlushMs;
    }

    public void setCacheFlushMs(long cacheFlushMs) {
        this.cacheFlushMs = cacheFlushMs;
    }
}
