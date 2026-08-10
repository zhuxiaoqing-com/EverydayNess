package org.evd.BootStrap;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.annotation.ServiceType;
import org.evd.game.common.ClassFinder;
import org.evd.game.runtime.config.GlobalConfig;
import org.evd.game.runtime.util.TimeUtils;
import org.evd.game.runtime.config.NodeConfig;
import org.evd.game.runtime.config.NodeInfo;
import org.evd.game.runtime.config.ScheduleInfo;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.support.TupleUtils;
import org.evd.game.runtime.support.TwoTuple;
import org.evd.game.runtime.annotation.Module;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

        log.info("start  node {} ", nodeId);
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

        // 预注册全部 Service，避免 Node 首个心跳在 Service 尚未创建时误判为空。
        for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()){
            for (ServiceInfo serviceInfo : scheduleInfo.getServices()){
                String className = serviceInfo.getClassName();
                String serviceClassName = "org.evd.game." + className + "." + className;
                Class<ServiceInfo> clazz = (Class<ServiceInfo>) Class.forName(serviceClassName);
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
                    String serviceName = GlobalConfig.getServiceName(serviceInfo, i);
                    Service service = (Service)con.newInstance(node, serviceName, scheduleInfo.getName(), serviceInfo.getInterval(), serviceInfo);
                    node.addService(service);
                }
            }
        }

        // 系统关闭时进行清理
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            boolean shutdownInterrupted = false;
            node.beginJvmShutdown();
            long currTime = System.currentTimeMillis();
            LogCore.core.info("ShutdownHook start!!!  {} ", TimeUtils.DateTimeUtils.getDateTimeOfTimestamp(currTime));
            try {
                for (TwoTuple<Integer, Method> ender : enders){
                    try {
                        ender.second.invoke(null, node);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        LogCore.core.error("关服 ender 执行失败，继续关闭后续资源: method={}", ender.second, e);
                    }
                }

                List<Service> list = node.getServices().values().stream()
                        .sorted(Comparator.comparingInt(a -> ServiceType.shutdownOrderId(a.getServiceType())))
                        .toList();

                int limitMill = 1000 * 60;
                for (Service service : list) {
                    service.postCoroutine(() -> service.stop(true));
                    long serviceStopStartMill = System.currentTimeMillis();
                    try {
                        service.closeFuture().toCompletableFuture().get(60, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        LogCore.core.error("关服时报错了！！！ service {} ", service.getId(), e);
                        continue;
                    } catch (TimeoutException e) {
                        LogCore.core.error("关服等待超过了 {} ，直接跳过！！！ service {} ", limitMill, service.getId());
                        service.logCoroutineDebugDump("shutdown timeout");
                        continue;
                    } catch (InterruptedException e) {
                        shutdownInterrupted = true;
                        Thread.interrupted();
                        LogCore.core.error("关闭钩子等待 Service 时被中断，继续关闭后续 Service: service={}",
                                service.getId(), e);
                        continue;
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
                LogCore.core.info("等待 Service 结束消耗毫秒 {} {} ", endTime - currTime, TimeUtils.DateTimeUtils.getDateTimeOfTimestamp(endTime));
                // TODO 处理service.close函数
                // TODO 处理各jar包的end函数
            } catch (RuntimeException e) {
                LogCore.core.error("关闭钩子执行异常", e);
            } finally {
                try {
                    try {
                        boolean forceNodeStop = !node.getServices().isEmpty();
                        node.requestStop(forceNodeStop);
                        node.closeFuture().toCompletableFuture().get(60, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        LogCore.core.error("关闭 Node 时发生错误: node={}", node.getId(), e);
                    } catch (TimeoutException e) {
                        LogCore.core.error("等待 Node 关闭超时: node={}", node.getId(), e);
                    } catch (InterruptedException e) {
                        shutdownInterrupted = true;
                        Thread.interrupted();
                        LogCore.core.error("关闭钩子等待 Node 时被中断: node={}", node.getId(), e);
                    } catch (RuntimeException e) {
                        LogCore.core.error("触发 Node 关闭时发生错误: node={}", node.getId(), e);
                    }
                } finally {
                    org.apache.logging.log4j.LogManager.shutdown();
                    if (shutdownInterrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }));

        // Node 启动前准备完整远程拓扑，避免启动期握手与配置注册竞争。
        for (NodeInfo remoteInfo : config.getNodes()){
            if (!node.getId().equals(remoteInfo.getName())){
                node.addRemoteNode(remoteInfo.getName(), remoteInfo.getAddr(), NodeInfo.needConnect(nodeInfo, remoteInfo));
            }
        }
        // Node 启动时会启动所有预注册 Service；每个 Service 在 onStart 中加入 Node 后再执行 init。
        node.startNode();

        LogCore.core.info("press ENTER to call System.exit() and run the shutdown routine.");
        String osName = System.getProperty("os.name");
        if (!osName.equalsIgnoreCase("linux")) {
            try {
                System.in.read();
            } catch (Exception e) {
                e.printStackTrace();
            }
            node.requestJvmShutdown();
        }
    }
}
