package org.evd.game.runtime.config;

import io.netty.util.internal.StringUtil;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.netty.AddressInfo;

public class ServiceInfo {
    private ServiceType serviceType;
    private String name;
    private int num = 1;
    private int interval = 5;
    private String publicAddr;
    private AddressInfo addressInfo;


    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getClassName() {
        return serviceType.getClassName();
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

    public AddressInfo getAddressInfo() {
        if(addressInfo == null && !StringUtil.isNullOrEmpty(publicAddr)) {
            addressInfo = new AddressInfo(publicAddr);
        }
        return addressInfo;
    }


    @Override
    public String toString() {
        return "ServiceInfo{" +
                "serviceType=" + serviceType +
                ", name='" + name + '\'' +
                ", num=" + num +
                ", interval=" + interval +
                ", publicAddr='" + publicAddr + '\'' +
                '}';
    }
}

