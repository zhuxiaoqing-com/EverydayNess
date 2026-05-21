package org.evd.BootStrap;

import org.evd.game.common.ClassFinder;
import org.evd.game.common.ConstPath;
import org.evd.BootStrap.config.NodeConfig;
import org.evd.BootStrap.config.NodeInfo;
import org.evd.BootStrap.config.ScheduleInfo;
import org.evd.BootStrap.config.ServiceInfo;
import org.evd.game.runtime.DistributeConfig;
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
    public static void main(String[] args) throws InterruptedException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
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

        String configPath = ConstPath.CONFIGURATION_PATH + bootStrapName;
        NodeConfig config = NodeConfig.load(configPath);
        registerServiceRoutes(config);

        final String nName = nodeId;
        Optional<NodeInfo> nodeInfoOptional = config.getNodes().stream().filter(n->n.getName().equals(nName)).findFirst();
        if (nodeInfoOptional.isEmpty()){
            LogCore.core.error("[{}] node config not exist", nodeId);
            return;
        }

        NodeInfo nodeInfo = nodeInfoOptional.get();
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
                Class<ServiceInfo> clazz = (Class<ServiceInfo>) Class.forName("org.evd.game." + serviceInfo.getClassName() + "." + serviceInfo.getClassName());
                if (clazz == null){
                    throw new SysException("service class not exist org.evd.service.{}", serviceInfo.getClassName());
                }
                // TODO 按service名加载 XXXService.jar


                Constructor con = clazz.getConstructor(Node.class, String.class, String.class, int.class);
                if (serviceInfo.getNum() < 0){
                    Service service = (Service)con.newInstance(node, serviceInfo.getName(), scheduleInfo.getName(), serviceInfo.getInterval());
                    applyServiceOptions(service, serviceInfo);
                    node.addService(service);
                    DistributeConfig.addSingleService(service);
                }else{
                    for (int i=1; i<=serviceInfo.getNum(); ++i){
                        Service service = (Service)con.newInstance(node, serviceInfo.getName() + i, scheduleInfo.getName(), serviceInfo.getInterval());
                        applyServiceOptions(service, serviceInfo);
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
                        ender.second.invoke(null);
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
                    String serviceClassName = "org.evd.game." + serviceInfo.getClassName() + "." + serviceInfo.getClassName();
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

    private static void applyServiceOptions(Service service, ServiceInfo serviceInfo) {
        Map<String, Object> options = serviceInfo.getOptions();
        if (options == null || options.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            applyServiceOption(service, entry.getKey(), entry.getValue());
        }
    }

    private static void applyServiceOption(Service service, String optionName, Object optionValue) {
        String setterName = "set" + Character.toUpperCase(optionName.charAt(0)) + optionName.substring(1);
        Method setter = findSetter(service.getClass(), setterName);
        if (setter == null) {
            throw new SysException("service option setter not found: service={}, option={}",
                    service.getClass().getName(), optionName);
        }
        Object convertedValue = convertOptionValue(service.getClass(), optionName, optionValue, setter.getParameterTypes()[0]);
        try {
            setter.invoke(service, convertedValue);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("apply service option failed: service=" + service.getClass().getName()
                    + ", option=" + optionName, e);
        }
    }

    private static Method findSetter(Class<?> type, String setterName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object convertOptionValue(Class<?> serviceType, String optionName, Object optionValue, Class<?> targetType) {
        if (optionValue == null) {
            if (targetType.isPrimitive()) {
                throw new SysException("service option value is null for primitive: service={}, option={}",
                        serviceType.getName(), optionName);
            }
            return null;
        }
        if (targetType.isInstance(optionValue)) {
            return optionValue;
        }
        if (targetType == String.class) {
            return String.valueOf(optionValue);
        }
        if (targetType == int.class || targetType == Integer.class) {
            return optionValue instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(optionValue));
        }
        if (targetType == long.class || targetType == Long.class) {
            return optionValue instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(optionValue));
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (optionValue instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(optionValue));
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) targetType, String.valueOf(optionValue));
        }
        throw new SysException("unsupported service option type: service={}, option={}, targetType={}",
                serviceType.getName(), optionName, targetType.getName());
    }

}
