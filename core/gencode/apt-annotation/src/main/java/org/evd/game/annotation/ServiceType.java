package org.evd.game.annotation;

import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
public enum ServiceType {
    CONN(1,"ConnService"),
    DB(2,"DBService"),
    LOC(3,"LocationService"),
    STAGE(4,"StageService"),
    PLAYER(5,"PlayerService"),
    GSTAGE(6,"StageService"),

    ;
    int type;
    String className;


    ServiceType(int type, String name) {
        this.type = type;
        this.className = name;
    }

    public int getType() {
        return type;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public String toString() {
        return "ServiceType{" +
                "type=" + type +
                ", name='" + className + '\'' +
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

    public static ServiceType byName(String className) {
        for (ServiceType serviceType : typeArray) {
            if(Objects.equals(serviceType.className, className)) {
                return serviceType;
            }
        }
        return null;
    }

    public  String fullClassName() {
        return "org.evd.game." + className + "." + className;
    }
}
