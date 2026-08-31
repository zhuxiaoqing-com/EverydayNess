package org.evd.game.runtime.ymlconfig;

/** MySQL 运行参数，与连接地址、认证信息分开配置。 */
public class DbMysqlRuntimeYml {
    private int remotePoolInitialSize = 4;
    private int remotePoolMaxSize = 16;
    private int localInitialSizePerService = 2;
    private int localMaxSizePerService = 6;
    private int localMaxPoolSize = 60;
    private int r2dbcIoWorkerCount = 4;
    private int serialLaneCount = 128;
    private int serialMaxPendingPerLane = 10000;

    public int getRemotePoolInitialSize() {
        return remotePoolInitialSize;
    }

    public void setRemotePoolInitialSize(int remotePoolInitialSize) {
        this.remotePoolInitialSize = remotePoolInitialSize;
    }

    public int getRemotePoolMaxSize() {
        return remotePoolMaxSize;
    }

    public void setRemotePoolMaxSize(int remotePoolMaxSize) {
        this.remotePoolMaxSize = remotePoolMaxSize;
    }

    public int getLocalInitialSizePerService() {
        return localInitialSizePerService;
    }

    public void setLocalInitialSizePerService(int localInitialSizePerService) {
        this.localInitialSizePerService = localInitialSizePerService;
    }

    public int getLocalMaxSizePerService() {
        return localMaxSizePerService;
    }

    public void setLocalMaxSizePerService(int localMaxSizePerService) {
        this.localMaxSizePerService = localMaxSizePerService;
    }

    public int getLocalMaxPoolSize() {
        return localMaxPoolSize;
    }

    public void setLocalMaxPoolSize(int localMaxPoolSize) {
        this.localMaxPoolSize = localMaxPoolSize;
    }

    public int getR2dbcIoWorkerCount() {
        return r2dbcIoWorkerCount;
    }

    public void setR2dbcIoWorkerCount(int r2dbcIoWorkerCount) {
        this.r2dbcIoWorkerCount = r2dbcIoWorkerCount;
    }

    public int getSerialLaneCount() {
        return serialLaneCount;
    }

    public void setSerialLaneCount(int serialLaneCount) {
        this.serialLaneCount = serialLaneCount;
    }

    public int getSerialMaxPendingPerLane() {
        return serialMaxPendingPerLane;
    }

    public void setSerialMaxPendingPerLane(int serialMaxPendingPerLane) {
        this.serialMaxPendingPerLane = serialMaxPendingPerLane;
    }
}
