package org.evd.BootStrap.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class ServiceInfo {
    private String className;
    private String name;
    private int num;
    private int interval = 5;
    private Map<String, Object> options = new LinkedHashMap<>();

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
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

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}

