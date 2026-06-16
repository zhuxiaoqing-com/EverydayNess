package org.evd.game.annotation;

import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
public enum ServiceType {
    ConnService(1,"ConnService"),
    DBService(2,"DBService"),
    LocationService(3,"LocationService"),
    StageService(4,"StageService"),
    PlayerService(5,"PlayerService"),

    ;
    int type;
    String name;


    ServiceType(int type, String name) {
        this.type = type;
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "ServiceType{" +
                "type=" + type +
                ", name='" + name + '\'' +
                '}';
    }

    static ServiceType[] typeArray;
    static {
        typeArray = values();
        Map<Integer, List<ServiceType>> collect = Arrays.stream(typeArray)
                .collect(Collectors.groupingBy(ServiceType::getType));
        for (List<ServiceType> value : collect.values()) {
            if (value.size() > 1) {
                log.error("ServiceType 有重复的type {}", value);
                throw new RuntimeException("ServiceType 有重复的type " + value);
            }
        }
    }

    public static ServiceType byType(int type) {
        for (ServiceType serviceType : typeArray) {
            if(serviceType.type ==  type) {
                return serviceType;
            }
        }
        return null;
    }

    public static ServiceType byName(String name) {
        for (ServiceType serviceType : typeArray) {
            if(Objects.equals(serviceType.name, name)) {
                return serviceType;
            }
        }
        return null;
    }
}
