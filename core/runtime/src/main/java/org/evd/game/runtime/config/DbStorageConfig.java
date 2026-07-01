package org.evd.game.runtime.config;

public class DbStorageConfig {
    private int batchThreshold = 30;
    private int batchPerCount = 500;
    private int costMsWarn = 100;
    private int batchCostMsWarn = 30000;
    private int writeBackFlushIntervalMs = 1000;
    private int writeBackCloseRetryIntervalMs = 1000;

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

    public int getWriteBackFlushIntervalMs() {
        return writeBackFlushIntervalMs;
    }

    public void setWriteBackFlushIntervalMs(int writeBackFlushIntervalMs) {
        this.writeBackFlushIntervalMs = writeBackFlushIntervalMs;
    }

    public int getWriteBackCloseRetryIntervalMs() {
        return writeBackCloseRetryIntervalMs;
    }

    public void setWriteBackCloseRetryIntervalMs(int writeBackCloseRetryIntervalMs) {
        this.writeBackCloseRetryIntervalMs = writeBackCloseRetryIntervalMs;
    }
}
