package org.evd.BootStrap;

import org.evd.game.common.ClassFinder;
import org.evd.game.common.GlobalConfig;
import org.evd.game.runtime.config.NodeConfig;
import org.evd.game.runtime.config.NodeInfo;
import org.evd.game.runtime.config.ScheduleInfo;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.config.DistributeConfig;
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

public class Main {
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

        registerServiceRoutes(config);

        NodeInfo nodeInfo = GlobalConfig.requireNodeInfo(nodeId);
        Node node = new Node(nodeId, nodeInfo.getAddr());
        for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()) {
            node.createExecutor(scheduleInfo.getName(), scheduleInfo.getNum());
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
                if (serviceInfo.getNum() < 0){
                    Service service = (Service)con.newInstance(node, serviceInfo.getName(), scheduleInfo.getName(), serviceInfo.getInterval(), serviceInfo);
                    node.addService(service);
                    DistributeConfig.addSingleService(service);
                }else{
                    for (int i=1; i<=serviceInfo.getNum(); ++i){
                        Service service = (Service)con.newInstance(node, serviceInfo.getName() + i, scheduleInfo.getName(), serviceInfo.getInterval(), serviceInfo);
                        node.addService(service);
                    }
                }
            }
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

        // 系统关闭时进行清理
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                for (TwoTuple<Integer, Method> ender : enders){
                    try {
                        ender.second.invoke(null, node);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
                // TODO 处理service.close函数
                // TODO 处理各jar包的end函数
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                LogCore.core.error("关闭钩子等待结束时被中断", e);
            }
        }));

    }

    private static void registerServiceRoutes(NodeConfig config) {
        for (NodeInfo configNode : config.getNodes()) {
            for (ScheduleInfo scheduleInfo : configNode.getSchedule()) {
                for (ServiceInfo serviceInfo : scheduleInfo.getServices()) {
                    String className = serviceInfo.getClassName();
                    String serviceClassName = "org.evd.game." + className + "." + className;
                    if (serviceInfo.getNum() < 0) {
                        DistributeConfig.addServiceNode(serviceClassName, new org.evd.game.runtime.call.CallPoint(configNode.getName(), serviceInfo.getName()));
                    } else {
                        for (int i = 1; i <= serviceInfo.getNum(); i++) {
                            DistributeConfig.addServiceNode(serviceClassName, new org.evd.game.runtime.call.CallPoint(configNode.getName(), serviceInfo.getName() + i));
                        }
                    }
                }
            }
        }
    }

}
