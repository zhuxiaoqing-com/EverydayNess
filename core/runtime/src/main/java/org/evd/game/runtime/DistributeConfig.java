package org.evd.game.runtime;

import org.evd.game.runtime.call.CallPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DistributeConfig {
    private final static ConcurrentHashMap<String, CallPoint> singletonService2Node = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<String, List<CallPoint>> serviceClass2Nodes = new ConcurrentHashMap<>();

    public static CallPoint getNode(String serviceName) {
        return singletonService2Node.get(serviceName);
    }

    public static void addSingleService(Service service) {
        singletonService2Node.put(service.getId(), new CallPoint(service.getNode().getId(), service.getId()));
    }

    public static void addServiceNode(String serviceClassName, CallPoint callPoint) {
        serviceClass2Nodes.compute(serviceClassName, (key, value) -> {
            List<CallPoint> callPoints = value == null ? new ArrayList<>() : new ArrayList<>(value);
            boolean exists = callPoints.stream()
                    .anyMatch(point -> point.getNodeId().equals(callPoint.getNodeId()) && point.getServId().equals(callPoint.getServId()));
            if (!exists) {
                callPoints.add(new CallPoint(callPoint));
                callPoints.sort(Comparator.comparing(CallPoint::getNodeId).thenComparing(CallPoint::getServId));
            }
            return callPoints;
        });
    }

    public static CallPoint getNodeByServiceClass(String serviceClassName, long routeKey) {
        List<CallPoint> callPoints = serviceClass2Nodes.get(serviceClassName);
        if (callPoints == null || callPoints.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(Long.hashCode(routeKey), callPoints.size());
        return new CallPoint(callPoints.get(index));
    }

    public static List<String> getServiceClassNames() {
        List<String> serviceClassNames = new ArrayList<>(serviceClass2Nodes.keySet());
        serviceClassNames.sort(String::compareTo);
        return serviceClassNames;
    }
}
