package org.evd.game.runtime.ymlconfig;

import org.evd.game.runtime.support.exception.SysException;

/** 登录准入和排队配置。 */
public final class LoginYml {
    private int maxOnline = 1_000;
    private int admissionsPerSecond = 20;
    private int maxQueueSize = 10_000;

    public int getMaxOnline() {
        return maxOnline;
    }

    public void setMaxOnline(int maxOnline) {
        this.maxOnline = maxOnline;
    }

    public int getAdmissionsPerSecond() {
        return admissionsPerSecond;
    }

    public void setAdmissionsPerSecond(int admissionsPerSecond) {
        this.admissionsPerSecond = admissionsPerSecond;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    void validate() {
        if (maxOnline <= 0) {
            throw new SysException("login.maxOnline must be positive: {}", maxOnline);
        }
        if (admissionsPerSecond <= 0) {
            throw new SysException("login.admissionsPerSecond must be positive: {}", admissionsPerSecond);
        }
        if (maxQueueSize <= 0) {
            throw new SysException("login.maxQueueSize must be positive: {}", maxQueueSize);
        }
    }
}
