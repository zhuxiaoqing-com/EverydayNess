package org.evd.game.runtime.config;

import io.netty.util.internal.StringUtil;
import org.evd.game.runtime.netty.AddressInfo;

import java.util.List;

public class NodeInfo {
    private String name;
    private String addr;
    private AddressInfo addressInfo;
    private List<ScheduleInfo> schedule;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public List<ScheduleInfo> getSchedule() {
        return schedule;
    }

    public void setSchedule(List<ScheduleInfo> schedule) {
        this.schedule = schedule;
    }


    public AddressInfo getAddressInfo() {
        if(addressInfo == null && StringUtil.isNullOrEmpty(addr)) {
            addressInfo = new AddressInfo(addr);
        }
        return addressInfo;
    }
}
