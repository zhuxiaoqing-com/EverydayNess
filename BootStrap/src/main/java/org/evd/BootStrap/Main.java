package org.evd.BootStrap;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.ClassFinder;
import org.evd.game.common.GlobalConfig;
import org.evd.game.runtime.TimeUtils;
import org.evd.game.runtime.config.NodeConfig;
import org.evd.game.runtime.config.NodeInfo;
import org.evd.game.runtime.config.ScheduleInfo;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.SysException;
import org.evd.game.runtime.support.TupleUtils;
import org.evd.game.runtime.support.TwoTuple;
import org.evd.game.runtime.annotation.Module;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;

@Slf4j
public class Main {
    private static void validateSingleServices(NodeConfig config) {
        Map<ServiceType, Integer> serviceCountMap = new EnumMap<>(ServiceType.class);
        Map<ServiceType, List<String>> serviceSourceMap = new EnumMap<>(ServiceType.class);
        for (NodeInfo nodeInfo : config.getNodes()) {
            if (nodeInfo.getSchedule() == null) {
                continue;
            }
            for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()) {
                if (scheduleInfo.getServices() == null) {
                    continue;
                }
                for (ServiceInfo serviceInfo : scheduleInfo.getServices()) {
                    if (serviceInfo == null || serviceInfo.getServiceType() == null) {
                        continue;
                    }
                    ServiceType serviceType = serviceInfo.getServiceType();
                    if (!serviceType.isSingle()) {
                        continue;
                    }
                    int instanceCount = Math.max(1, serviceInfo.getNum());
                    serviceCountMap.merge(serviceType, instanceCount, Integer::sum);
                    serviceSourceMap.computeIfAbsent(serviceType, key -> new ArrayList<>())
                            .add(String.format("node=%s,schedule=%s,service=%s,num=%d",
                                    nodeInfo.getName(),
                                    scheduleInfo.getName(),
                                    serviceInfo.getName(),
                                    instanceCount));
                }
            }
        }

        for (Map.Entry<ServiceType, Integer> entry : serviceCountMap.entrySet()) {
            if (entry.getValue() > 1) {
                throw new SysException("single service type duplicated: {} total={} detail={}",
                        entry.getKey(), entry.getValue(), serviceSourceMap.get(entry.getKey()));
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] args) throws Exception {
//        if (args.length < 2){
//            Log.error("param error");
//            Log.error("     Param1: BootStrap file name");
//            Log.error("     Param2: Name of node");
//
//            throw new SysException("param Error");
//        }
        String bootStrapName = "Bootstrap-all.yml";
        String nodeId = "node1";
        if (args.length > 0){
            bootStrapName = args[0];
        }
        if (args.length > 1){
            nodeId = args[1];
        }

        GlobalConfig.init(bootStrapName);
        NodeConfig config = GlobalConfig.requireNodeConfig();
        validateSingleServices(config);

        NodeInfo nodeInfo = GlobalConfig.requireNodeInfo(nodeId);
        Node node = new Node(nodeId, nodeInfo);
        for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()) {
            node.createExecutor(scheduleInfo.getName(), scheduleInfo.getNum());
        }

        List<TwoTuple<Integer, Method>> starters = new ArrayList<>();
        List<TwoTuple<Integer, Method>> enders = new ArrayList<>();
        List<Class<?>> sources = ClassFinder.getAllClass("org.evd.game");
        for (Class<?> clazz : sources){
            if (clazz.isAnnotationPresent(Module.class)){
                for (Method method : clazz.getDeclaredMethods()){
                    if (Modifier.isStatic(method.getModifiers())){
                        Module.OnStart starter = method.getAnnotation(Module.OnStart.class);
                        Module.OnEnd ender = method.getAnnotation(Module.OnEnd.class);
                        if (starter != null){
                            starters.add(TupleUtils.tuple(starter.priority(), method));
                        }
                        if (ender != null){
                            enders.add(TupleUtils.tuple(ender.priority(), method));
                        }
                    }

                }
            }
        }
        // 按starter的优先级排序
        starters.sort(Comparator.comparingInt(o -> o.first));
        enders.sort(Comparator.comparingInt(o -> o.first));
        for (TwoTuple<Integer, Method> starter : starters){
            starter.second.invoke(null, node);
        }

        // 节点启动
        node.start();
        // addRemoteNode
        for (NodeInfo remoteInfo : config.getNodes()){
            if (!node.getId().equals(remoteInfo.getName())){
                node.addRemoteNode(remoteInfo.getName(), remoteInfo.getAddr());
            }
        }
        // addService
        for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()){
            for (ServiceInfo serviceInfo : scheduleInfo.getServices()){
                String className = serviceInfo.getClassName();
                String serviceClassName = "org.evd.game." + className + "." + className;
                Class<ServiceInfo> clazz = (Class<ServiceInfo>) Class.forName(serviceClassName);
                if (clazz == null){
                    throw new SysException("service class not exist {}", serviceClassName);
                }
                // TODO 按service名加载 XXXService.jar
                Constructor con = clazz.getConstructor(Node.class, String.class, String.class, int.class, ServiceInfo.class);
           /*     if (serviceInfo.getNum() < 0){
                    Service service = (Service)con.newInstance(node, serviceInfo.getName(), scheduleInfo.getName(), serviceInfo.getInterval(), serviceInfo);
                    node.addService(service);
                }else{
                    for (int i=1; i<=serviceInfo.getNum(); ++i){
                        Service service = (Service)con.newInstance(node, serviceInfo.getName() + i, scheduleInfo.getName(), serviceInfo.getInterval(), serviceInfo);
                        node.addService(service);
                    }
                }*/

                int num = serviceInfo.getNum();
                num = Math.max(1, num);
                for (int i = 1; i<= num; ++i){
                    Service service = (Service)con.newInstance(node, serviceInfo.getName() + i, scheduleInfo.getName(), serviceInfo.getInterval(), serviceInfo);
                    node.addService(service);
                }
            }
        }

        // 系统关闭时进行清理
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long currTime = System.currentTimeMillis();
            LogCore.core.info("ShutdownHook start!!!  {} ", TimeUtils.DateTimeUtils.getDateTimeOfTimestamp(currTime));
            try {
                for (TwoTuple<Integer, Method> ender : enders){
                    try {
                        ender.second.invoke(null, node);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }

                List<Service> list = node.getServices().values().stream()
                        .sorted(Comparator.comparingInt(a -> ServiceType.shutdownOrderId(a.getServiceType())))
                        .toList();

                int limitMill = 1000 * 20;
                for (Service service : list) {
                    service.postCoroutine(service::stop);
                    long serviceStopStartMill = System.currentTimeMillis();
                    while (node.getServices().containsKey(service.getId())) {
                        Thread.sleep(50);
                        if (System.currentTimeMillis() - serviceStopStartMill > limitMill) {
                            // 等待超过了秒进行警告
                            LogCore.core.error("关服等待超过了 {} ，直接跳过！！！ service {} ", limitMill, service.getId());
                            service.logCoroutineDebugDump("shutdown timeout");
                            break;
                        }
                    }
                    LogCore.core.warn("关服 service {} costMill {} ", service.getId(), System.currentTimeMillis() - serviceStopStartMill);
                }

                /*while (!node.getServices().isEmpty()) {
                    Thread.sleep(500);
                    long mill = System.currentTimeMillis();
                    int limitMill = 1000 * 20;
                    if (mill - currTime > limitMill) {
                        // 等待超过了秒进行警告
                        LogCore.core.error("关服等待超过了 {} ，直接关闭！！！ service {} ", limitMill, node.getServices());
                        for (Service service : node.getServices().values()) {
                            service.logCoroutineDebugDump("shutdown timeout");
                        }
                        break;
                    }
                }*/
                long endTime = System.currentTimeMillis();
                LogCore.core.info("等待node结束消耗毫秒 {} {} ", endTime - currTime, TimeUtils.DateTimeUtils.getDateTimeOfTimestamp(endTime));
                org.apache.logging.log4j.LogManager.shutdown();
                // TODO 处理service.close函数
                // TODO 处理各jar包的end函数
            } catch (InterruptedException e) {
                LogCore.core.error("关闭钩子等待结束时被中断", e);
            }
        }));

        LogCore.core.info("press ENTER to call System.exit() and run the shutdown routine.");
        String osName = System.getProperty("os.name");
        if (!osName.equalsIgnoreCase("linux")) {
            try {
                System.in.read();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        }
    }
}
