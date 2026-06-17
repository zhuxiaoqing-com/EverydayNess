package org.evd.game.runtime.config;

import org.evd.game.annotation.ServiceType;

public class ServiceInfo {
    private String serviceType;
    private String name;
    private int num;
    private int interval = 5;
    private String publicAddr;

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getClassName() {
        return ServiceType.valueOf(serviceType).getClassName();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public String getPublicAddr() {
        return publicAddr;
    }

    public void setPublicAddr(String publicAddr) {
        this.publicAddr = publicAddr;
    }
}

