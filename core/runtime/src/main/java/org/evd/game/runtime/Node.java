package org.evd.game.runtime;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.call.*;
import org.evd.game.runtime.config.NodeInfo;
import org.evd.game.runtime.config.RegisteredService;
import org.evd.game.runtime.netty.*;
import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.SysException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 节点，代表一个进程
 */
@Slf4j
public class Node extends TickCase{
    /** 远程节点 */
    protected final ConcurrentMap<String, RemoteNode> remoteNodes = new ConcurrentHashMap<>();
    /** 发送给远程note的call请求 */
    private final ConcurrentLinkedQueue<RemoteCall> remoteCalls = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CallBase> callBases = new ConcurrentLinkedQueue<>();

    /** 多个线程池，把有阻塞service和非阻塞service放到不同的线程 */
    private final List<ScheduledExecutor> scheduledExecutors = new ArrayList<>();
    /** node包含的services */
    private final ConcurrentHashMap<Object, Service> services = new ConcurrentHashMap<>();
    /** 远程node -> services镜像 */
    private final ConcurrentHashMap<String, List<RegisteredService>> remoteNodeServices = new ConcurrentHashMap<>();
    /** serviceType -> services缓存 */
    private volatile Map<ServiceType, List<RegisteredService>> type2ServiceMap = new HashMap<>();
    private volatile Map<CallPoint, RegisteredService> callPoint2ServiceMap = new HashMap<>();
    private volatile Map<ServiceType, List<CallPoint>> type2CallMap = new HashMap<>();
    /** 地址 */
    private final String addr;
    private final NodeInfo nodeInfo;
    /** 本次心跳要发送给远程note的call请求 */
    private final List<RemoteCall> affirmRemoteCalls = new ArrayList<>();

//    /** ZMQ上下文 */
//    protected final ZContext zmqContext;
//    /** ZMQ连接 */
//    protected final ZMQ.Socket zmqPull;

    private final byte[] remoteReceiveBuffer = BufferPool.allocate();
    /** 远程Node调用定时器 */
    private final TickTimer remoteNodePulseTimer = new TickTimer(RemoteNode.INTERVAL_PING, true);
    /** 本地服务注册版本 */
    private volatile AtomicLong localServiceVersion = new AtomicLong();
    /** 本地服务注册是否有变化 */
    private long syncLocalServicesDirty;

    private volatile NetAcceptor acceptor;
    ChannelManager channelManager = new ChannelManager();

    public Node(String name, NodeInfo nodeInfo){
        super(name, 1);
        this.nodeInfo = nodeInfo;
        this.addr = nodeInfo.getAddr();

        int port = nodeInfo.getAddressInfo().getPort();
        acceptor = new NetAcceptor(port,
                new BaseChannelInitializer(new NodeChannelHandler(channelManager, this), true));
        LogCore.core.info("Netty 启动完成: node={}, port={}", getId(), port);

      /*  this.zmqContext = new ZContext();
        this.zmqPull = zmqContext.createSocket(SocketType.PULL);
        this.zmqPull.setLinger(3000);

        LogCore.core.info("节点【{}】绑定地址【{}】", name, addr);
        // 绑定到通用地址，这样通过内网和外网地址都可以连接上
        String addrWC = RegExUtils.replacePattern(addr, "\\d+.\\d+.\\d+.\\d+", "*");
        this.zmqPull.bind(addrWC);*/

        bindScheduledExecutor(new ScheduledExecutor(name, 1));

    }

    public void createExecutor(String name, int threadNum){
        if (status != CaseStatus.New){
            return;
        }

        scheduledExecutors.add(new ScheduledExecutor(name, threadNum));
    }

    @Override
    protected void pulse() {
        // 确认本次心跳要发送的remoteCall
        pulseAffirmRemoteCall_nt();
        // 发送remoteCall
        pulseSendRemoteCall_nt();
        //pulseCallPuller_nt();
        //处理其他Node发送过来的Call调用
        pulseCallBaseProcess();
        //调用远程Node的心跳操作
        pulseRemoteNodes_nt();
        // 本地服务注册变化后，广播给已连接节点
        pulseServiceRegistry_nt();
    }

    private void pulseAffirmRemoteCall_nt() {
        // 本心跳要执行的call
        RemoteCall call;
        while ((call = remoteCalls.poll()) != null) {
            affirmRemoteCalls.add(call);
        }
    }

    private void pulseSendRemoteCall_nt() {
        for (RemoteCall call : affirmRemoteCalls){
            sendCall(call);
        }
        affirmRemoteCalls.clear();
    }

    /**
     * 接受其他Node发送过来的Call请求
     */
  /*  private void pulseCallPuller_nt() {
        while (true) {
            try {
                // 接受到的字节流长度
                // zmq是基于块传输的 所以不用考虑流切割的问题
                int recvLen = zmqPull.recv(remoteReceiveBuffer, 0, remoteReceiveBuffer.length, ZMQ.DONTWAIT);
                // 如果长度<=0 代表没有接到数据 本心跳接收任务结束
                if (recvLen <= 0) {
                    break;
                }

                // 处理Call请求
                remoteCallHandle_nt(remoteReceiveBuffer, recvLen);
            } catch(Exception e) {
                // 吞掉并打印异常
                LogCore.core.error("", e);
            }
        }
    }*/
    /**
     * 处理Call请求
     */
    public void remoteCallHandle_nt(ByteBuf msg, Channel sourceChannel) {
        int len = msg.readableBytes();
        msg.getBytes(msg.readerIndex(), remoteReceiveBuffer, 0, len);
        // 转化为输出流
        InputStream input = new InputStream(remoteReceiveBuffer, 0, len);
        // 是否已读取到末尾
        while (!input.isAtEnd()) {
            // 先读取一个Call请求
            CallBase call = input.read();
            call.setSourceChannel(sourceChannel);
            callBases.add(call);
        }
    }

    private void pulseCallBaseProcess() {
        CallBase callBase;
        while ((callBase = callBases.poll()) != null) {
            callHandle_snt(callBase);
        }
    }


    /**
     * 发送RemoteCall
     * @param call
     */
    private void sendCall(RemoteCall call) {
        RemoteNode node = remoteNodes.get(call.getRemoteNodeId());
        if (node != null) {
            node.send(call.getBuffer());
        } else {
            LogCore.remote.error("发送Call请求时，发现未知远程节点: call={}", call);
        }
    }

    /**
     * 调用远程Node的心跳操作
     */
    private void pulseRemoteNodes_nt() {
        // 检查时间间隔
        if (!remoteNodePulseTimer.isPeriod(timeCurrent)) {
            return;
        }

        // 遍历远程Node
        for (RemoteNode r : remoteNodes.values()) {
            r.pulse();
        }
    }

    private void pulseServiceRegistry_nt() {
        long version = localServiceVersion.get();
        if (version == syncLocalServicesDirty) {
            return;
        }
        syncLocalServicesDirty = version;

        refreshLocalServices();
        for (RemoteNode remoteNode : remoteNodes.values()) {
            if (!remoteNode.isActive()) {
                continue;
            }
            sendLocalServicesToRemote_nt(remoteNode);
        }
    }

    /**
     * 启动
     * @throws RuntimeException
     */
    @Override
    protected void onStart() {
//        if (scheduledExecutors.isEmpty()){
//            throw new SysException("node还为创建线程池");
//        }

        List<Service> pendingAdd = new ArrayList<>();
        for (Map.Entry<Object, Service> entry: services.entrySet()){
            pendingAdd.add(entry.getValue());
        }
        // 清理services，后面会重新addService
        services.clear();

        // addService
        for (Service service : pendingAdd){
            addService(service);
        }
    }

    /**
     * 创建任务异步添加到service
     * @param service
     */
    public void addService(Service service){
        // node还未启动，services起到pending暂存的作用
        if (status == CaseStatus.New){
            services.put(service.getId(), service);
        }else{
            Optional<ScheduledExecutor> result = scheduledExecutors.stream().filter(s->s.getName().equals(service.getScheduledName())).findFirst();
            if (result.isEmpty()){
                LogCore.core.error("[{}]服务找不到对应的调度器[{}]", service.getId(), service.getScheduledName());
                return;
            }
            ScheduledExecutor scheduledExecutor = result.get();
            service.bindScheduledExecutor(scheduledExecutor);
            service.start();
        }
    }

    void attachToNode(Service service){
        services.put(service.getId(), service);
        localServiceVersion.incrementAndGet();
    }


    /**
     * 发送请求
     * @param nodeId
     * @param buffer
     * @param bufferLength
     */
    public void flushCall_st(String nodeId, byte[] buffer, int bufferLength) {
        // 同一Node下 无需走传输协议 内部直接接收即可
        if (id.equals(nodeId)) {
            InputStream input = new InputStream(buffer, 0, bufferLength);
            localCallHandle_st(input);
            // 其余的需要通过远程Node来发送请求值目标Node
        } else {
            byte[] copy = new byte[bufferLength];
            System.arraycopy(buffer, 0, copy, 0, bufferLength);

            remoteCalls.add(new RemoteCall(nodeId, copy));
//			RemoteNode node = remoteNodes.get(nodeId);
//			if (node != null) {
//				node.addCall(buffer, bufferLength);
//			} else {
//				logRemote.error("发送Call请求时，发现未知远程节点: nodeId={}", nodeId);
//			}
        }
    }

    /**
     * 处理Call请求
     */
    public void localCallHandle_st(InputStream input){

        // 是否已读取到末尾
        while (!input.isAtEnd()) {
            CallBase call = input.read();
            try {
                callHandle_snt(call);
            } catch (Exception e) {
                LogCore.core.error("localCallHandle_st error call {}", call, e);
            }
        }
    }

    /**
     * 处理接收到的Call请求
     */
    public void callHandle_snt(CallBase call) {
        Channel sourceChannel = call.getSourceChannel();
        if (sourceChannel != null
                && sourceChannel.attr(ServerAttributeKey.remoteNodeId).get() == null
                && !(call instanceof CallNodeServicesSync)) {
            LogCore.remote.error("收到未完成远程节点握手的非法消息: node={}, callType={}, remoteNode={}",
                    getId(), call.getClass().getSimpleName(), call.from == null ? null : call.from.nodeId);
            sourceChannel.close();
            return;
        }
        // 根据请求类型来分别处理
        switch (call) {
            case ActorMessage ignored: {
                Service service = services.get(call.to.servId);
                service.addCall_snt(call);
            }
            break;
            // PRC远程调用请求
            case Call ignored: {
                Service service = services.get(call.to.servId);
                // 请求分发
                service.addCall_snt(call);
            }
            break;
            // PRC远程调用请求的返回值
            case CallResult ignored: {
                Service service = services.get(call.to.servId);
                service.addCall_snt(call);
            }
            break;

            case CallNodeServicesSync callNodeServicesSync: {
                RemoteNode node = remoteNodes.get(call.from.nodeId);
                if (node == null) {
                    node = addRemoteNode(call.from.nodeId, callNodeServicesSync.getAddr());
                }
                node.onNodeServicesSync_nt(sourceChannel, callNodeServicesSync.isInit());
                syncRemoteServices_nt(call.from.nodeId, callNodeServicesSync.getServices());
            }
            break;
            case CallPing callPing: {
                RemoteNode node = remoteNodes.get(call.from.nodeId);
                if (node == null) {
                    LogCore.remote.warn("收到未知远程node的ping: nodeId={}", call.from.nodeId);
                    break;
                }
                node.onPing_nt(sourceChannel);
            }
            break;
            case CallPong ignored: {
                RemoteNode node = remoteNodes.get(call.from.nodeId);
                if (node == null) {
                    LogCore.remote.warn("收到未知远程node的pong: nodeId={}", call.from.nodeId);
                    break;
                }
                node.onPong_nt(sourceChannel);
            }
            break;
            default:
                throw new SysException("Unexpected call type: {}" + call.getClass());
        }
    }

    /**
     * 添加远程Node
     * @param name
     * @param addr
     */
    public RemoteNode addRemoteNode(String name, String addr) {
        return addRemoteNode(name, addr, false);
    }

    public RemoteNode addRemoteNode(String name, String addr, boolean needConnect) {
        RemoteNode remote = remoteNodes.get(name);
        if (remote != null) {
            return remote;
        }

        RemoteNode newRemote = new RemoteNode(this, name, addr, needConnect);
        RemoteNode oldRemote = remoteNodes.putIfAbsent(name, newRemote);
        if (oldRemote != null) {
            return oldRemote;
        }

        LogCore.remote.info("添加远程node：name={},addr={},needConnect={}", name, addr, needConnect);
        return newRemote;
    }


    public void remove(Service service) {
        services.remove(service.getId());
        localServiceVersion.incrementAndGet();
    }

    public String getAddr() {
        return addr;
    }

    public void onRemoteNodeDisconnected_nt(RemoteNode remoteNode) {
        remoteNodeServices.remove(remoteNode.getRemoteId());
        rebuildServiceIndexes();
    }

    public void onInboundChannelInactive_nt(Channel channel) {
        if (channel == null) {
            return;
        }
        String remoteNodeId = channel.attr(ServerAttributeKey.remoteNodeId).get();
        if (remoteNodeId == null) {
            return;
        }
        RemoteNode remoteNode = remoteNodes.get(remoteNodeId);
        if (remoteNode != null && remoteNode.onChannelDown(channel)) {
            onRemoteNodeDisconnected_nt(remoteNode);
        }
    }

    private void refreshLocalServices() {
        List<RegisteredService> snapshot = buildLocalServicesSnapshot();
        remoteNodeServices.put(id, snapshot);
        rebuildServiceIndexes();
    }

    private void syncRemoteServices_nt(String nodeId, List<RegisteredService> services) {
        remoteNodeServices.put(nodeId, services);
        rebuildServiceIndexes();
    }

    private void sendLocalServicesToRemote_nt(RemoteNode remoteNode) {
        CallNodeServicesSync call = new CallNodeServicesSync();
        call.from = new CallPoint(id, null);
        call.to = new CallPoint(remoteNode.getRemoteId(), null);
        call.setInit(false);
        call.setAddr(addr);
        call.setServices(buildLocalServicesSnapshot());
        remoteNode.sendCall(call);
    }

    List<RegisteredService> buildLocalServicesSnapshot() {
        List<RegisteredService> snapshot = new ArrayList<>();
        for (Service service : services.values()) {
            if (service == null || service.serviceInfo == null) {
                continue;
            }

            String shortClassName = service.serviceInfo.getClassName();
            snapshot.add(new RegisteredService(
                    service.serviceInfo.getServiceType(),
                    "org.evd.game." + shortClassName + "." + shortClassName,
                    service.getId(),
                    id));
        }
        snapshot.sort(Comparator.comparing(RegisteredService::getServiceClassName)
                .thenComparing(RegisteredService::getServiceId));
        return snapshot;
    }

    private void rebuildServiceIndexes() {
        Map<CallPoint, RegisteredService> oldMap = new HashMap<>(callPoint2ServiceMap);
        List<RegisteredService> addList = new ArrayList<>();
        List<RegisteredService> removeList = new ArrayList<>();

        Map<ServiceType, List<RegisteredService>> tempType2ServiceMap = new HashMap<>();
        Map<CallPoint, RegisteredService> tempCallPoint2ServiceMap = new HashMap<>();
        Map<ServiceType, List<CallPoint>> tempType2CallMap = new HashMap<>();
        for (List<RegisteredService> nodeServices : remoteNodeServices.values()) {
            if (nodeServices == null) {
                continue;
            }
            for (RegisteredService service : nodeServices) {
                if (service == null || service.getServiceType() == null) {
                    continue;
                }
                tempType2ServiceMap.computeIfAbsent(service.getServiceType(), key -> new ArrayList<>())
                        .add(new RegisteredService(service));

                tempCallPoint2ServiceMap.put(new CallPoint(service.getNodeId(), service.getServiceId()), service);
                tempType2CallMap.computeIfAbsent(service.getServiceType(), key -> new ArrayList<>())
                        .add(new RegisteredService(service).getCallPoint());
            }
        }
        for (List<RegisteredService> services : tempType2ServiceMap.values()) {
            services.sort(Comparator.comparing(RegisteredService::getNodeId)
                    .thenComparing(RegisteredService::getServiceId));
        }
        for (List<CallPoint> value : tempType2CallMap.values()) {
            value.sort(Comparator.comparing(CallPoint::getNodeId)
                    .thenComparing(CallPoint::getServId));
        }


        type2ServiceMap = RuntimeUtils.convertModifyListMap(tempType2ServiceMap);
        callPoint2ServiceMap = tempCallPoint2ServiceMap;
        type2CallMap = RuntimeUtils.convertModifyListMap(tempType2CallMap);


        HashSet<CallPoint> eachKey = new HashSet<>();
        eachKey.addAll(oldMap.keySet());
        eachKey.addAll(tempCallPoint2ServiceMap.keySet());

        for (CallPoint callPoint : eachKey) {
            boolean oldContain = oldMap.containsKey(callPoint);
            boolean newContain = tempCallPoint2ServiceMap.containsKey(callPoint);
            if (oldContain && newContain) {
                continue;
            }
            if (newContain) {
                addList.add(tempCallPoint2ServiceMap.get(callPoint));
            }
            if (oldContain) {
                removeList.add(oldMap.get(callPoint));
            }
        }


        // 给每一个Service触发
        for (Service value : services.values()) {
            if (!addList.isEmpty()) {
                value.postCoroutine(() -> value.onServiceConnect(addList));
            }

            if (!removeList.isEmpty()) {
                value.postCoroutine(() -> value.onServiceDisconnect(removeList));
            }
        }


    }
    public List<RegisteredService> getServicesByType(ServiceType serviceType) {
        return type2ServiceMap.getOrDefault(serviceType,Collections.emptyList());
    }

    public List<CallPoint> getCallPointByType(ServiceType serviceType) {
        return type2CallMap.getOrDefault(serviceType, Collections.emptyList());
    }

    public CallPoint getAnyCallPointByType(ServiceType serviceType) {
        List<RegisteredService> registeredServices = type2ServiceMap.getOrDefault(serviceType,Collections.emptyList());
        return registeredServices.isEmpty() ? null : registeredServices.getFirst().getCallPoint();
    }

    public ConcurrentHashMap<Object, Service> getServices() {
        return services;
    }

}


