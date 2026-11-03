package org.evd.game.runtime.ymlconfig;

public class DbSnapshotStoreYml {
    private boolean enabled = true;
    private String directory = "data/db-snapshots";
    private int averageValueSizeKb = 1;
    private long entries;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDirectory() {
        if (directory == null || directory.isBlank()) {
            throw new IllegalStateException("snapshotStore.directory 不能为空");
        }
        return directory;
    }

    public void setDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("snapshotStore.directory 不能为空");
        }
        this.directory = directory;
    }

    public int getAverageValueSizeKb() {
        return averageValueSizeKb;
    }

    public void setAverageValueSizeKb(int averageValueSizeKb) {
        this.averageValueSizeKb = averageValueSizeKb;
    }

    public long getEntries() {
        return entries;
    }

    public void setEntries(long entries) {
        this.entries = entries;
    }

    public long getAverageValueSizeBytes() {
        if (averageValueSizeKb <= 0) {
            throw new IllegalStateException("snapshotStore.averageValueSizeKb 必须大于 0: "
                    + averageValueSizeKb);
        }
        return Math.multiplyExact((long) averageValueSizeKb, 1024L);
    }
}
