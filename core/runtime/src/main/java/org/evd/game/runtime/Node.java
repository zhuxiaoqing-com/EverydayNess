package org.evd.game.runtime;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.annotation.node.NodeType;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.*;
import org.evd.game.runtime.ymlconfig.NodeInfo;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.Db.NodeDbExecutor;
import org.evd.game.runtime.debug.DebugPrint;
import org.evd.game.runtime.misc.BufferPool;
import org.evd.game.runtime.misc.ScheduledExecutor;
import org.evd.game.runtime.netty.*;
import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serializeBean.NodeFrameChunk;
import org.evd.game.runtime.serializeBean.TickTimer;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RpcErrorCodes;
import org.evd.game.runtime.support.exception.InboundBusinessException;
import org.evd.game.runtime.support.exception.ServiceStoppingException;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.util.RuntimeUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 节点，代表一个进程
 */
@Slf4j
public class Node extends TickCase{
    /** 新发现的 Service 在正式发布到路由索引前的稳定等待时间。 */
    private static final long SERVICE_PENDING_TIME = 10_000L;
    /** 离线 Service 状态巡检间隔。 */
    private static final long OFFLINE_SERVICE_CHECK_INTERVAL = 5_000L;
    /** 离线 Service 保留时间。 */
    private static final long OFFLINE_SERVICE_RETENTION_TIME = 5 * 60 * 1000L;

    /** 远程节点 */
    protected final ConcurrentMap<CallPoint, RemoteNode> remoteNodes = new ConcurrentHashMap<>();
    /** 非 Node 线程投递的 Node 事件，例如入站 Call 与连接断开后的状态清理。 */
    private final FrameQueue<Runnable> postedTasks = new FrameQueue<>(new ConcurrentLinkedQueue<>());

    /** 多个线程池，把有阻塞service和非阻塞service放到不同的线程 */
    private final List<ScheduledExecutor> scheduledExecutors = new ArrayList<>();
    /** 驱动 Node 心跳的调度器。 */
    private final ScheduledExecutor nodeExecutor;
    /** node包含的services */
    private final ConcurrentHashMap<Object, Service> services = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Object, Service> pendServices = new ConcurrentHashMap<>();
    /** 远程node -> services镜像 */
    private final ConcurrentHashMap<CallPoint, List<RegisteredService>> remoteNodeServices = new ConcurrentHashMap<>();
    /** 已从服务索引中移除、但仍需保留一段时间的离线 Service。仅由 Node 线程访问。 */
    private final ConcurrentMap<CallPoint, RegisteredService> offlineServices = new ConcurrentHashMap<>();
    /** 当前所有已发现的 Service；pendingStartTime 大于 0 表示仍处于 Pending，仅由 Node 线程访问。 */
    private Map<CallPoint, RegisteredService> allServiceMap = new HashMap<>();
    /** serviceType -> services缓存 */
    private volatile Map<ServiceType, List<RegisteredService>> type2ServiceMap = new HashMap<>();
    private volatile Map<CallPoint, RegisteredService> callPoint2ServiceMap = new HashMap<>();
    private volatile Map<ServiceType, List<CallPoint>> type2CallMap = new HashMap<>();
    /** 地址 */
    private final String addr;
    private final NodeInfo nodeInfo;
    private final int platformId;
    private final int serverId;
    private final CallPoint nodeCallPoint;

    public NodeType getNodeType() {
        return nodeInfo.getNodeType();
    }

    /** 本帧需要在 Node 线程执行的投递事件。 */
    private final List<Runnable> affirmPostedTasks = new ArrayList<>();
//    /** ZMQ上下文 */
//    protected final ZContext zmqContext;
//    /** ZMQ连接 */
//    protected final ZMQ.Socket zmqPull;

    /** 远程Node调用定时器 */
    private final TickTimer remoteNodePulseTimer = new TickTimer(RemoteNode.INTERVAL_PING, true);
    /** 离线 Service 巡检定时器 */
    private final TickTimer offlineServiceTimer = new TickTimer(OFFLINE_SERVICE_CHECK_INTERVAL, true);
    /** 本地服务注册版本 */
    private volatile AtomicLong localServiceVersion = new AtomicLong();
    /** 关服已经开始；关闭新的 Service 注册入口。 */
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    /** 本地服务注册是否有变化 */
    private long syncLocalServicesDirty;

    private volatile NetAcceptor acceptor;
    /** NODE_LOCAL 模式下由 Node 独占的数据库入口，不是 Service。 */
    private volatile NodeDbExecutor nodeDbExecutor;
    /** NODE_LOCAL 模式数据库入口的固定路由标识。 */
    private final CallPoint nodeDbCallPoint;
    ChannelManager channelManager = new ChannelManager();

    public Node(NodeInfo nodeInfo, int platformId, int serverId){
        super(Integer.toString(nodeInfo.getNodeId()), 1);
        this.nodeInfo = nodeInfo;
        this.platformId = platformId;
        this.serverId = serverId;
        this.nodeCallPoint = new CallPoint(platformId, serverId, nodeInfo.getNodeId(), null);
        this.addr = nodeInfo.getAddr();
        this.nodeDbCallPoint = getCallPoint("$node-db");

        int port = nodeInfo.getAddressInfo().getPort();
        acceptor = new NetAcceptor(port,
                new BaseChannelInitializer(() -> new NodeChannelHandler(channelManager, this), false));
        LogCore.core.info("Netty 启动完成: node={}, port={}", getId(), port);

      /*  this.zmqContext = new ZContext();
        this.zmqPull = zmqContext.createSocket(SocketType.PULL);
        this.zmqPull.setLinger(3000);

        LogCore.core.info("节点【{}】绑定地址【{}】", name, addr);
        // 绑定到通用地址，这样通过内网和外网地址都可以连接上
        String addrWC = RegExUtils.replacePattern(addr, "\\d+.\\d+.\\d+.\\d+", "*");
        this.zmqPull.bind(addrWC);*/

        nodeExecutor = new ScheduledExecutor(getId(), 1);
        bindScheduledExecutor(nodeExecutor);

    }

    public void createExecutor(String name, int threadNum){
        if (status != CaseStatus.New){
            return;
        }

        scheduledExecutors.add(new ScheduledExecutor(name, threadNum));
    }


    private long currentTickTime_nt() {
        long tickTime = getTimeCurrent();
        return tickTime > 0L ? tickTime : System.currentTimeMillis();
    }

    @Override
    protected void pulse() {
        // 启动期间持续扫描所有 Service；任一服务关闭即关闭整个 Node。
        pulseServiceLifecycle_nt();
        if (getStatus() != CaseStatus.Running) {
            return;
        }
        pulseAffirmPostedTasks_nt();
        pulsePostedTasks_nt();
        //调用远程Node的心跳操作
        pulseRemoteNodes_nt();
        // 本地服务注册变化后，广播给已连接节点
        pulseServiceRegistry_nt();
        // 定期发布已完成稳定等待的 Service，并清理离线 Service
        checkServiceTimeouts_nt();
    }

    private void pulseServiceLifecycle_nt() {
        if (status != CaseStatus.Starting && status != CaseStatus.Running) {
            return;
        }

        // Admin 已按全局顺序关闭完本机 Service，Node 再关闭自己的网络和线程资源。
        if (services.isEmpty() && status == CaseStatus.Running) {
            stopAfterServicesClosed_nt();
            return;
        }

        // 启动状态下，等所有的都启动完成，node才算是启动完成，遍历pendService 检测状态,
        // 或者有一个启动失败就是失败
        if (status == CaseStatus.Starting) {
            boolean allRunning = true;
            for (Service service : pendServices.values()) {
                CaseStatus serviceStatus = service.getStatus();
                // 有一个在启动中状态不对就关闭;
                if (serviceStatus == CaseStatus.Closed) {
                    stopForServiceTermination_nt(service, serviceStatus);
                    return;
                }
                // 有一个启动没有完成就 false
                if (serviceStatus != CaseStatus.Running) {
                    allRunning = false;
                }
            }

            if (allRunning) {
                markRunning();
                localServiceVersion.incrementAndGet();
                LogCore.core.info("node startup completed: node={}, services={}", id, services.keySet());
            }
        }
    }

    private void stopForServiceTermination_nt(Service service, CaseStatus serviceStatus) {
        if (status != CaseStatus.Starting && status != CaseStatus.Running) {
            return;
        }
        LogCore.core.error("service terminated, shutting down node: node={}, service={}, status={}",
                id, service == null ? null : service.getId(), serviceStatus);
        requestJvmShutdown();
    }

    public String getName() {
        return nodeInfo.getName();
    }

    /**
     * 正常 Admin 停服路径：先完整关闭 Node，等 closeFuture 完成后再退出进程。
     * 不能先调用 System.exit，否则 ShutdownHook 会反过来承担正常关闭流程。
     */
    private void stopAfterServicesClosed_nt() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        LogCore.core.info("all services closed, stopping node: node={}", id);
        closeFuture().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LogCore.core.error("node close failed before process exit: node={}", id, failure);
            }
            requestProcessExit();
        });
        // Service 已经全部关闭；这里的 force 只用于保证 Node 网络资源收尾失败时，
        // closeFuture 仍会异常完成并触发进程退出，避免永久卡在 shutdownStarted。
        stop(true);
    }

    /**
     * 这里不能在 Node 心跳线程里直接调用 System.exit(0)，
     * 因为 System.exit 会等待 ShutdownHook，
     * 而 ShutdownHook 中 Service 的远程传输依赖 Node 心跳线程，会互相等待。
     */
    public void requestJvmShutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        requestProcessExit();
    }

    private void requestProcessExit() {
        Thread shutdownThread = new Thread(() -> System.exit(0), "node-shutdown-" + id);
        shutdownThread.start();
    }

    /** ShutdownHook 开始后关闭新 Service 注册，但保留远程传输供 Service 完成清理。 */
    public synchronized void beginJvmShutdown() {
        shutdownStarted.set(true);
    }

    /** Bootstrap 唯一启动入口，避免 ShutdownHook 与 Node 启动交叉。 */
    public synchronized void startNode() {
        if (shutdownStarted.get()) {
            throw new SysException("cannot start node after JVM shutdown begins: node={}", id);
        }
        start();
    }

    /** 从外部线程请求关闭；已启动的 Node 始终回到自己的单线程执行器完成 stop。 */
    public synchronized void requestStop(boolean force) {
        if (isStopping()) {
            return;
        }
        if (status == CaseStatus.New) {
            stop(force);
            return;
        }
        // 启动尚未完成时不再等待 Service 优雅退出，避免异步初始化与 Node 关闭交叉。
        boolean actualForce = force || status == CaseStatus.Starting;
        nodeExecutor.submit(() -> stop(actualForce));
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


    private void pulseAffirmPostedTasks_nt() {
        int tasksToProcess = postedTasks.getFrameProcessNum();
        for (int i = 0; i < tasksToProcess; i++) {
            Runnable task = postedTasks.poll();
            if (task == null) {
                return;
            }
            affirmPostedTasks.add(task);
        }
    }

    private void pulsePostedTasks_nt() {
        for (Runnable task : affirmPostedTasks) {
            try {
                task.run();
            } catch (Throwable e) {
                if (e instanceof VirtualMachineError virtualMachineError) {
                    throw virtualMachineError;
                }
                LogCore.core.error("node posted task failed: node={}", id, e);
            }
        }
        affirmPostedTasks.clear();
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
        pulseInboundRemoteChannelsTimeout_nt();
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


    private void sendCallResult(CallResult result) {
        if (isLocalNode(result.to)) {
            Service sourceService = services.get(result.to.servId);
            if (sourceService == null) {
                LogCore.remote.error("local rpc rejection result cannot be delivered: node={}, targetService={}, waitId={}",
                        id, result.to.servId, result.id);
            } else {
                sourceService.addCall_snt(result);
            }
            return;
        }
        if (!sendCallResultOnSource(result)) {
            LogCore.remote.warn("远程 RPC 结果原 Session 不可写，丢弃结果: node={}, remoteNode={}, sessionId={}, waitId={}",
                    id, result.to.nodeId, result.getSourceSessionId(), result.id);
        }
    }

    boolean sendCallResultOnSource(CallResult result) {
        if (result == null || result.to == null) {
            return false;
        }
        RemoteNode remoteNode = getRemoteNode(result.to);
        return remoteNode != null && remoteNode.sendCallOnSession(result, result.getSourceSessionId());
    }

    void postCallResultOnSource(CallResult result) {
        post(() -> {
            if (!sendCallResultOnSource(result)) {
                LogCore.remote.warn(
                        "远程 RPC 结果原 Session 不可写，丢弃结果: node={}, remoteNode={}, sessionId={}, waitId={}",
                        id,
                        result == null || result.to == null ? null : result.to.nodeId,
                        result == null ? -1L : result.getSourceSessionId(),
                        result == null ? 0L : result.id);
            }
        });
    }

    /**
     * 发送RemoteCall
     * @param call
     */
    private void sendCall(RemoteCall call) {
        RemoteNode node = remoteNodes.get(call.getRemoteNodePoint());
        if (node == null || !node.send(call.getPacket(), call.getExpectedSessionId())) {
            LogCore.remote.error("发送Call请求失败: remoteNode={}, call={}", call.getRemoteNodeId(), call);
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
        // addService
        for (Service service : pendServices.values()){
            addService(service);
        }
    }

    /**
     * 关服逻辑要写这里，等这个方法结束就结束，协程运行
     *
     * @param force
     */
    @Override
    protected void onStopInternal(boolean force) {
        super.onStopInternal(force);
        if (!force && !services.isEmpty()) {
            throw new SysException(
                    "cannot stop node before services close: node={}, services={}",
                    id,
                    services.keySet());
        }
        if (force && !services.isEmpty()) {
            LogCore.core.warn("force closing node with services still present: node={}, services={}",
                    id, services.keySet());
        }

        NetAcceptor currentAcceptor = acceptor;
        acceptor = null;
        if (currentAcceptor != null) {
            currentAcceptor.shutdown();
        }

        for (RemoteNode remoteNode : remoteNodes.values()) {
            remoteNode.close();
        }
        NetConnector.shutdownSharedGroup();

        if (nodeDbExecutor != null) {
            nodeDbExecutor.close();
            nodeDbExecutor = null;
        }
    }

    private void checkServiceTimeouts_nt() {
        long now = currentTickTime_nt();
        if (!offlineServiceTimer.isPeriod(now)) {
            return;
        }

        List<RegisteredService> readyList = new ArrayList<>();
        for (RegisteredService service : allServiceMap.values()) {
            long pendingStartTime = service.getPendingStartTime();
            if (pendingStartTime <= 0L || now - pendingStartTime < SERVICE_PENDING_TIME) {
                continue;
            }
            service.setPendingStartTime(0L);
            readyList.add(service);
        }
        if (!readyList.isEmpty()) {
            rebuildServiceRouteMaps_nt();
            notifyServiceEvent_nt(readyList, "service connect ready", Service::onServiceConnectReady);
        }

        List<RegisteredService> expiredList = new ArrayList<>();
        for (Map.Entry<CallPoint, RegisteredService> entry : offlineServices.entrySet()) {
            RegisteredService offlineService = entry.getValue();
            boolean online = callPoint2ServiceMap.containsKey(entry.getKey());
            boolean expired = now - offlineService.getOfflineMill() >= OFFLINE_SERVICE_RETENTION_TIME;
            if (online || expired) {
                LogCore.core.info("清理离线Service: node={}, callPoint={}, offlineMill={}, elapsedMill={}, reason={}",
                        id, entry.getKey(), offlineService.getOfflineMill(),
                        now - offlineService.getOfflineMill(), online ? "重新上线" : "离线超时");
                if (offlineServices.remove(entry.getKey(), offlineService) && expired && !online) {
                    expiredList.add(offlineService);
                }
            }
        }
        notifyServiceEvent_nt(expiredList, "service offline expired", Service::onServiceOfflineExpired);
    }

    @Override
    protected void onClose() {
        nodeExecutor.shutdown();
        for (ScheduledExecutor scheduledExecutor : scheduledExecutors) {
            scheduledExecutor.shutdown();
        }
        scheduledExecutors.clear();

        postedTasks.clear();
        affirmPostedTasks.clear();
        services.clear();
        pendServices.clear();
        remoteNodes.clear();
        remoteNodeServices.clear();
        allServiceMap = Map.of();
        offlineServiceTimer.stop();
        offlineServices.clear();
        type2ServiceMap = Map.of();
        callPoint2ServiceMap = Map.of();
        type2CallMap = Map.of();
        channelManager.clear();

        // JVM 退出由 Bootstrap 或 requestJvmShutdown 负责；Node 这里只完成资源收尾。
    }

    /**
     * 创建任务异步添加到service
     * @param service
     */
    public synchronized void addService(Service service){
        if (shutdownStarted.get()) {
            throw new SysException("cannot add service after JVM shutdown begins: node={}, service={}",
                    id, service == null ? null : service.getId());
        }
        // node还未启动，services起到pending暂存的作用
        if (status == CaseStatus.New){
            Service existing = pendServices.putIfAbsent(service.getId(), service);
            if (existing != null && existing != service) {
                throw new SysException("duplicate service id: {}", service.getId());
            }
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
        registerService(service);
    }

    private void registerService(Service service) {
        Service existing = services.putIfAbsent(service.getId(), service);
        if (existing != null && existing != service) {
            throw new SysException("duplicate service id: {}", service.getId());
        }
    }

    void publishService(Service service) {
        if (services.get(service.getId()) != service || service.getStatus() != CaseStatus.Running) {
            throw new SysException("cannot publish service before initialization: {}", service.getId());
        }
        localServiceVersion.incrementAndGet();
    }

    /**
     * 发送请求
     * @param nodePoint 目标 Node 点位
     * @param sessionId 目标 RemoteSession
     * @param buffer 序列化数据
     * @param bufferLength 有效数据长度
     */
    public boolean flushCall_st(CallPoint nodePoint, long sessionId, byte[] buffer, int bufferLength) {
        // 同一Node下 无需走传输协议 内部直接接收即可
        if (isLocalNode(nodePoint)) {
            InputStream input = new InputStream(buffer, 0, bufferLength);
            while (!input.isAtEnd()) {
                postLocalCall(input.read());
            }
            return true;
            // 其余的需要通过远程Node来发送请求值目标Node
        } else {
            RemoteNode remoteNode = remoteNodes.get(nodePoint);
            if (remoteNode == null) {
                return false;
            }
            RemoteCall remoteCall = new RemoteCall(
                    nodePoint, sessionId, NodeFrameChunk.wrap(buffer, bufferLength));
            post(() -> sendCall(remoteCall));
            return true;
        }
    }

    /**
     * 这里返回能发送，后面的所有不能发送都不再处理发送失败;
     */
    public RemoteSession captureRemoteSession(CallBase call) {
        if (call == null || call.to == null) {
            return null;
        }
        RemoteNode remoteNode = getRemoteNode(call.to);
        return remoteNode == null ? null : remoteNode.captureSession(call);
    }

    /** 仅检查目标 Session 的连接状态，用于检查已序列化但尚未满帧的数据。 */
    public boolean canSendOutboundSession_nt(CallPoint nodePoint, long sessionId) {
        if (nodePoint == null || sessionId < 0L) {
            return false;
        }
        if (isLocalNode(nodePoint)) {
            return sessionId == 0L;
        }
        RemoteNode remoteNode = remoteNodes.get(nodePoint);
        return remoteNode != null && remoteNode.isCurrentSession(sessionId);
    }

    /**
     * 处理Call请求
     */
    public void localCallHandle_st(InputStream input){

        // 是否已读取到末尾
        while (!input.isAtEnd()) {
            CallBase call = input.read();
            handleInboundCall(call, null);
        }
    }

    void postLocalCall(CallBase call) {
        post(() -> handleInboundCall(call, null));
    }

    public void onOutboundChannelActive(RemoteNode remoteNode, NetChannel channel) {
        post(() -> remoteNode.onOutboundChannelActive(channel));
    }


    /**
     * 添加远程Node
     * @param name
     * @param addr
     */
    public RemoteNode addRemoteNode(int nodeId, String addr) {
        return addRemoteNode(getCallPoint(null, nodeId).nodePoint(), addr, false);
    }

    public synchronized RemoteNode addRemoteNode(int nodeId, String addr, boolean needConnect) {
        return addRemoteNode(getCallPoint(null, nodeId).nodePoint(), addr, needConnect);
    }

    public synchronized RemoteNode addRemoteNode(CallPoint remoteNodePoint, String addr, boolean needConnect) {
        if (shutdownStarted.get()
                && status != CaseStatus.Starting
                && status != CaseStatus.Running) {
            throw new SysException("cannot add remote node after JVM shutdown begins: node={}, remoteNode={}", id, remoteNodePoint);
        }
        CallPoint nodePoint = remoteNodePoint.nodePoint();
        RemoteNode remote = remoteNodes.get(nodePoint);
        if (remote != null) {
            return remote;
        }

        RemoteNode newRemote = new RemoteNode(this, nodePoint, addr, needConnect);
        RemoteNode oldRemote = remoteNodes.putIfAbsent(nodePoint, newRemote);
        if (oldRemote != null) {
            return oldRemote;
        }

        LogCore.remote.info("添加远程node：name={},addr={},needConnect={}", nodePoint, addr, needConnect);
        return newRemote;
    }


    public void remove(Service service) {
        post(() -> removeService_nt(service));
    }

    private void removeService_nt(Service service) {
        services.remove(service.getId(), service);
        localServiceVersion.incrementAndGet();
    }

    public String getAddr() {
        return addr;
    }

    /**
     * 处理Call请求
     */
    public void remoteCallHandle_nt(ByteBuf msg, NetChannel sourceChannel) {
        int len = msg.readableBytes();
        Integer remoteNodeId = sourceChannel == null ? null : sourceChannel.getChannel().attr(ServerAttributeKey.remoteNodeId).get();
        DebugPrint.printReceiveNodeFrame(getNodeId(), remoteNodeId, sourceChannel, len);

        byte[] receiveBuffer = BufferPool.allocate();
        try {
            if (len > receiveBuffer.length) {
                LogCore.remote.error("node inbound frame exceeds receive buffer: node={}, remoteNode={}, len={}, capacity={}",
                        id, remoteNodeId, len, receiveBuffer.length);
                if (sourceChannel != null) {
                    sourceChannel.close();
                }
                return;
            }
            msg.getBytes(msg.readerIndex(), receiveBuffer, 0, len);
            InputStream input = new InputStream(receiveBuffer, 0, len);
            while (!input.isAtEnd()) {
                CallBase call = input.read();
                post(() -> handleInboundCall(call, sourceChannel));
            }
        } finally {
            BufferPool.deallocate(receiveBuffer);
        }
    }


    public void onChannelInactive_nt(NetChannel channel) {
        if (channel == null) {
            return;
        }
        post(() -> handleChannelInactive_nt(channel));
    }

    /** 仅在 Node 线程处理连接断开，保证与该连接已入队的入站消息保持顺序。 */
    private void handleChannelInactive_nt(NetChannel channel) {
        Integer remoteNodeId = channel.getChannel().attr(ServerAttributeKey.remoteNodeId).get();
        RemoteSession session = channel.getChannel().attr(ServerAttributeKey.remoteSession).get();
        if (session != null) {
            remoteNodeId = session.getRemoteNodeId();
        }
        if (remoteNodeId == null) {
            return;
        }
        CallPoint remoteNodePoint = channel.getChannel().attr(ServerAttributeKey.remoteCallPoint).get();
        RemoteNode remoteNode = remoteNodePoint == null ? null : remoteNodes.get(remoteNodePoint.nodePoint());
        if (remoteNode != null && remoteNode.onChannelInactive_nt(channel, session)) {
            remoteNodeServices.remove(remoteNode.getRemoteCallPoint());
            refreshAllServiceMap_nt();
        }
        if (session != null) {
            failRpcWaitsForRemote_nt(remoteNodeId, session);
        }
    }

    /**
     * 供 Netty 等外部线程安全投递任务到 Node 线程执行。
     * 同一 channel 的入站 Call 和断链事件由同一 EventLoop 串行投递，
     * 因而会以相同顺序在 Node 线程处理。
     */
    public void post(Runnable task) {
        if (task == null) {
            throw new SysException("posted node task is null: node={}", id);
        }
        postedTasks.add(task);
    }

    public synchronized void setNodeDbExecutor(NodeDbExecutor nodeDbExecutor) {
        if (status != CaseStatus.New) {
            throw new SysException("cannot set node database after node starts: node={}", id);
        }
        if (this.nodeDbExecutor != null) {
            throw new SysException("node database already exists: node={}", id);
        }
        this.nodeDbExecutor = Objects.requireNonNull(nodeDbExecutor, "nodeDbExecutor");
    }

    public boolean hasNodeDbExecutor() {
        return nodeDbExecutor != null;
    }

    public NodeDbExecutor getNodeDbExecutor() {
        return nodeDbExecutor;
    }

    public CallPoint getNodeDbCallPoint() {
        return nodeDbCallPoint;
    }

    private void failRpcWaitsForRemote_nt(Integer remoteNodeId, RemoteSession session) {
        for (Service service : services.values()) {
            if (service == null) {
                continue;
            }
            try {
                service.post(() -> {
                    int failed = service.failRpcWaitsForRemote(session.getRemoteCallPoint(), session.getSessionId());
                    if (failed > 0) {
                        LogCore.remote.warn("远程Node物理连接断开，结束对应 Session RPC等待: localNode={}, remoteNode={}, sessionId={}, channelId={}, service={}, count={}",
                                id, remoteNodeId, session.getSessionId(), session.getChannelId(), service.getId(), failed);
                    }
                });
            } catch (RuntimeException e) {
                LogCore.remote.error("远程Node断开时投递RPC等待清理失败: localNode={}, remoteNode={}, sessionId={}, channelId={}, service={}",
                        id, remoteNodeId, session.getSessionId(), session.getChannelId(), service.getId(), e);
            }
        }
    }

    /**
     * 处理接收到的Call请求
     */
    public void callHandle_snt(CallBase call, NetChannel sourceChannel) {
        if (call.getFrom() == null || call.getTo() == null) {
            /*if (sourceChannel != null) {
                sourceChannel.close();
            }*/
            throw new SysException("rpc call point is missing: callType={}", call.getClass().getSimpleName());
        }
        if (!isLocalNode(call.getTo())) {
            /*if (sourceChannel != null) {
                sourceChannel.close();
            }*/
            throw new SysException("rpc target node mismatch: localNode={}, targetNode={}, callType={}",
                    id, call.getTo().nodeId, call.getClass().getSimpleName());
        }

        //  sourceChannel==null,说明是同node的消息
        if (sourceChannel != null) {
            Integer attrRemoteNodeId = sourceChannel.getChannel().attr(ServerAttributeKey.remoteNodeId).get();
            CallPoint attrRemoteNodePoint = sourceChannel.getChannel().attr(ServerAttributeKey.remoteCallPoint).get();
            boolean initialHandshake = call instanceof CallNodeServicesSync sync && sync.isInit();
            if (attrRemoteNodeId == null) {
                if (!initialHandshake) {
                    LogCore.remote.error("收到未完成远程节点握手的非法消息: node={}, callType={}, remoteNode={}",
                            getId(), call.getClass().getSimpleName(), call.from == null ? null : call.from.nodeId);
                    sourceChannel.close();
                    return;
                }
            } else if (initialHandshake) {
                LogCore.remote.error("已绑定 Session 的连接重复发送初始化握手: node={}, remoteNode={}, channelId={}",
                        getId(), attrRemoteNodeId, sourceChannel.getChannelId());
                sourceChannel.close();
                return;
            }
            if (!initialHandshake && call.getSourceSessionId() < 0L) {
                LogCore.remote.error("收到未绑定 Session 的远程调用: node={}, callType={}, remoteNode={}",
                        getId(), call.getClass().getSimpleName(), call.from == null ? null : call.from.nodeId);
                sourceChannel.close();
                return;
            }
            RemoteNode remoteNode = attrRemoteNodePoint == null ? null : remoteNodes.get(attrRemoteNodePoint.nodePoint());
            if (!initialHandshake
                    && (remoteNode == null || !remoteNode.isCurrentSession(call.getSourceSessionId()))) {
                LogCore.remote.error("收到已过期 Session 的远程调用: node={}, callType={}, remoteNode={}, sessionId={}",
                        getId(), call.getClass().getSimpleName(), attrRemoteNodeId, call.getSourceSessionId());
                sourceChannel.close();
                return;
            }
            if (attrRemoteNodePoint != null && !attrRemoteNodePoint.sameNode(call.from)) {
                LogCore.remote.error("收到 from 节点身份与连接不一致的非法消息: node={}, callType={}, from.remoteNode={}  attr.remoteNode={}",
                        getId(), call.getClass().getSimpleName(), call.from, attrRemoteNodePoint);
                return;
            }
        }

        DebugPrint.printReceiveRpc(call);

        CallPoint remoteNodePoint = call.from.nodePoint();
        // 根据请求类型来分别处理
        switch (call) {
            case RpcCallBase ignored: {
                if(ignored instanceof CallServiceStop callServiceStop){
                    // service 不存在 或者已经关了，直接返回成功
                    Service service = services.get(call.to.servId);
                    if(service == null || service.isStopping()) {
                        CallServiceStopResult callServiceStopResult = new CallServiceStopResult(true, "服务器已关闭");
                        CallResult aReturn = callServiceStop.createReturn();
                        aReturn.result = callServiceStopResult;
                        sendCallResult(aReturn);
                        break;
                    }
                }
                if (status != CaseStatus.Running) {
                    throw new InboundBusinessException(RpcErrorCodes.SERVICE_NOT_READY, "node not ready");
                }
                Service service = services.get(call.to.servId);
                if (service == null) {
                    throw new InboundBusinessException(RpcErrorCodes.SERVICE_NOT_FOUND, "target service missing");
                } else if (service.getStatus() != CaseStatus.Running) {
                    if (service.isStopping()) {
                        throw new ServiceStoppingException("target service is stopping");
                    } else {
                        throw new InboundBusinessException(RpcErrorCodes.SERVICE_NOT_READY, "target service not ready");
                    }
                } else {
                    service.addCall_snt(call);
                }
            }
            break;
            // PRC远程调用请求
         /*   case Call ignored: {
                Service service = services.get(call.to.servId);
                // 请求分发
                if (service == null) {
                    throw new InboundBusinessException(RpcErrorCodes.SERVICE_NOT_FOUND, "target service missing");
                } else if (service.getStatus() != CaseStatus.Running) {
                    throw new InboundBusinessException(RpcErrorCodes.SERVICE_NOT_READY, "target service not ready");
                } else {
                    service.addCall_snt(call);
                }
            }
            break;*/
            // PRC远程调用请求的返回值
            case CallResult callResult: {
                Service service = services.get(call.to.servId);
                if (service == null) {
                    LogCore.remote.error("rpc result cannot be delivered: node={}, targetService={}, waitId={}, reason={}",
                            id, call.to.servId, call.id, "service missing");
                } else {
                    service.post(() -> service.handleInboundResult_st(callResult));
                }
            }
            break;

            case CallNodeServicesSync callNodeServicesSync: {
                RemoteNode node = remoteNodes.get(remoteNodePoint);
                if (node == null) {
                    node = addRemoteNode(remoteNodePoint, callNodeServicesSync.getAddr(), false);
                }
                if (callNodeServicesSync.isInit() && !node.onNodeServicesSync_nt(sourceChannel)) {
                    break;
                }
                syncRemoteServices_nt(remoteNodePoint, callNodeServicesSync.getServices());
            }
            break;
            case CallPing callPing: {
                onRemotePing_nt(remoteNodePoint, sourceChannel, callPing);
            }
            break;
            case CallPong callPong: {
                onRemotePong_nt(remoteNodePoint, sourceChannel, callPong);
            }
            break;
            default:
                throw new SysException("Unexpected call type: {}" + call.getClass());
        }
    }

    /**
     * 统一处理入站Call，负责把节点路由阶段的业务拒绝转换为RPC失败响应。
     */
    private void handleInboundCall(CallBase call, NetChannel sourceChannel) {
        if (sourceChannel != null) {
            RemoteSession sourceSession = sourceChannel.getChannel().attr(ServerAttributeKey.remoteSession).get();
            call.setSourceSessionId(sourceSession == null ? -1L : sourceSession.getSessionId());
        } else {
            call.setSourceSessionId(0L);
        }
        try {
            callHandle_snt(call, sourceChannel);
        } catch (InboundBusinessException e) {
            LogCore.remote.error("rpc inbound rejected: node={}, from={}, to={}, callType={}, reason={}",
                    id, call.getFrom(), call.getTo(), call.getClass().getSimpleName(), e.getMessage());
            sendInboundBusinessFailure(call, e);
        } catch (Exception e) {
            LogCore.remote.error("node inbound call failed: node={}, callType={}, from={}, to={}",
                    id,
                    call.getClass().getSimpleName(),
                    call.getFrom(),
                    call.getTo(),
                    e);
        }
    }

    private void sendInboundBusinessFailure(CallBase call, InboundBusinessException exception) {
        CallResult result;
        if (call instanceof RpcCallBase request && request.isNeedResult()) {
            result = request.createReturn();
        } else {
            return;
        }
        result.setSuccess(false);
        result.setErrorCode(exception.getErrorCode());
        result.setErrorMessage(exception.getMessage());
        sendCallResult(result);
    }



    private void onRemotePing_nt(CallPoint remoteNodePoint, NetChannel sourceChannel, CallPing ping) {
        RemoteNode remoteNode = remoteNodes.get(remoteNodePoint);
        if (remoteNode == null) {
            LogCore.remote.warn("收到未知远程node的ping: node={}", remoteNodePoint);
            return;
        }
        if (sourceChannel == null) {
            LogCore.remote.warn("收到远程node ping时连接不存在: node={}", remoteNodePoint);
            return;
        }
        remoteNode.updateServiceStatuses_nt(ping.getServiceStatuses());
        sourceChannel.setLastPingTime(currentTickTime_nt());

        CallPong call = new CallPong();
        call.from = getNodeCallPoint();
        call.to = remoteNode.getRemoteCallPoint();
        call.setServiceStatuses(buildLocalServiceStatuses_nt());
        remoteNode.sendCall(call);
    }

    private void onRemotePong_nt(CallPoint remoteNodePoint, NetChannel sourceChannel, CallPong pong) {
        RemoteNode remoteNode = remoteNodes.get(remoteNodePoint);
        if (remoteNode == null) {
            LogCore.remote.warn("收到未知远程node的pong: node={}", remoteNodePoint);
            return;
        }
        remoteNode.updateServiceStatuses_nt(pong.getServiceStatuses());
        sourceChannel.setLastPingTime(currentTickTime_nt());
    }


    private void pulseInboundRemoteChannelsTimeout_nt() {
        long timeCurr = currentTickTime_nt();
        for (NetChannel netChannel : channelManager.getChannelMap().values()) {
            Integer remoteNodeId = netChannel.getChannel().attr(ServerAttributeKey.remoteNodeId).get();

            long lastActivityTime = netChannel.getLastPingTime();
            if (lastActivityTime <= 0L || (timeCurr - lastActivityTime) <= RemoteNode.INTERVAL_LOST) {
                continue;
            }
            LogCore.remote.warn("node checkTimeout localNode={}, remoteNode={}, sessionId={} timeCurr {} lastActivityTime {}",
                    getId(), remoteNodeId, netChannel.getChannelId(), timeCurr, lastActivityTime);
            netChannel.close();
        }
    }

    private void refreshLocalServices() {
        List<RegisteredService> snapshot = buildLocalServicesSnapshot();
        remoteNodeServices.put(getNodeCallPoint(), snapshot);
        refreshAllServiceMap_nt();
    }

    private void syncRemoteServices_nt(CallPoint nodePoint, List<RegisteredService> services) {
        remoteNodeServices.put(nodePoint, services);
        refreshAllServiceMap_nt();
    }

    private void sendLocalServicesToRemote_nt(RemoteNode remoteNode) {
        CallNodeServicesSync call = new CallNodeServicesSync();
        call.from = getNodeCallPoint();
        call.to = remoteNode.getRemoteCallPoint();
        call.setInit(false);
        call.setAddr(addr);
        call.setServices(buildLocalServicesSnapshot());
        remoteNode.sendCall(call);
    }

    /** 在 Node 线程内生成本地 Service 状态快照，随 Ping/Pong 发送。 */
    List<NodeServiceStatus> buildLocalServiceStatuses_nt() {
        List<NodeServiceStatus> snapshot = new ArrayList<>();
        for (Service service : services.values()) {
            if (service == null ||service.getStatus() != CaseStatus.Running) {
                continue;
            }
            Service.PressureSnapshot pressure = service.pressureSnapshot();
            snapshot.add(new NodeServiceStatus(
                    service.getId(),
                    pressure.readyContinuations()));
        }
        snapshot.sort(Comparator.comparing(NodeServiceStatus::getServiceId,
                Comparator.nullsFirst(String::compareTo)));
        return snapshot;
    }

    List<RegisteredService> buildLocalServicesSnapshot() {
        if (status != CaseStatus.Running) {
            return List.of();
        }
        List<RegisteredService> snapshot = new ArrayList<>();
        for (Service service : services.values()) {
            if (service == null || service.serviceInfo == null
                    || service.getStatus() != CaseStatus.Running) {
                continue;
            }

            String shortClassName = service.serviceInfo.getClassName();
            snapshot.add(new RegisteredService(
                    service.serviceInfo.getServiceType(),
                    "org.evd.game." + shortClassName + "." + shortClassName,
                    service.getId(),
                    platformId,
                    serverId,
                    nodeInfo.getNodeId()));
        }
        snapshot.sort(Comparator.comparing(RegisteredService::getServiceClassName)
                .thenComparing(RegisteredService::getServiceId));
        return snapshot;
    }

    private void refreshAllServiceMap_nt() {
        Map<CallPoint, RegisteredService> oldAllServiceMap = allServiceMap;
        List<RegisteredService> addList = new ArrayList<>();
        List<RegisteredService> removeList = new ArrayList<>();

        Map<CallPoint, RegisteredService> currentServiceMap = new HashMap<>();
        for (List<RegisteredService> nodeServices : remoteNodeServices.values()) {
            if (nodeServices == null) {
                continue;
            }
            for (RegisteredService service : nodeServices) {
                if (service == null || service.getServiceType() == null) {
                    continue;
                }
                RegisteredService currentService = new RegisteredService(service);
                currentServiceMap.put(currentService.getCallPoint(), currentService);
            }
        }

        long now = currentTickTime_nt();
        Map<CallPoint, RegisteredService> newAllServiceMap = new HashMap<>();
        for (Map.Entry<CallPoint, RegisteredService> entry : currentServiceMap.entrySet()) {
            RegisteredService currentService = entry.getValue();
            RegisteredService oldService = oldAllServiceMap.get(entry.getKey());
            if (oldService == null) {
                currentService.setPendingStartTime(now);
                addList.add(currentService);
                LogCore.core.info("Service进入Pending: node={}, callPoint={}, pendingStartTime={}, service={}",
                        id, entry.getKey(), currentService.getPendingStartTime(), currentService);
            } else {
                currentService.setPendingStartTime(oldService.getPendingStartTime());
            }
            newAllServiceMap.put(entry.getKey(), currentService);
        }

        for (Map.Entry<CallPoint, RegisteredService> entry : oldAllServiceMap.entrySet()) {
            if (newAllServiceMap.containsKey(entry.getKey())) {
                continue;
            }
            RegisteredService oldService = entry.getValue();
            removeList.add(oldService);
            LogCore.core.info("Service下线: node={}, callPoint={}, service={}",
                    id, entry.getKey(), oldService);
            if (oldService.getPendingStartTime() == 0L) {
                RegisteredService offlineService = new RegisteredService(oldService);
                offlineService.setOfflineMill(now);
                offlineServices.put(entry.getKey(), offlineService);
            }
        }

        allServiceMap = Map.copyOf(newAllServiceMap);
        rebuildServiceRouteMaps_nt();

        notifyServiceEvent_nt(addList, "service connect", Service::onServiceConnect);
        notifyServiceEvent_nt(removeList, "service disconnect", Service::onServiceDisconnect);
    }

    /** 从当前所有 Service 中构建已经结束 Pending 的三个正式路由索引。 */
    private void rebuildServiceRouteMaps_nt() {
        Map<ServiceType, List<RegisteredService>> tempType2ServiceMap = new HashMap<>();
        Map<ServiceType, List<CallPoint>> tempType2CallMap = new HashMap<>();
        Map<CallPoint, RegisteredService> tempCallPoint2ServiceMap = new HashMap<>();
        for (RegisteredService service : allServiceMap.values()) {
            if (service.getPendingStartTime() != 0L) {
                continue;
            }
            tempCallPoint2ServiceMap.put(service.getCallPoint(), service);
            tempType2ServiceMap.computeIfAbsent(service.getServiceType(), key -> new ArrayList<>())
                    .add(new RegisteredService(service));
            tempType2CallMap.computeIfAbsent(service.getServiceType(), key -> new ArrayList<>())
                    .add(service.getCallPoint());
        }
        for (List<RegisteredService> services : tempType2ServiceMap.values()) {
            services.sort(Comparator.comparing(RegisteredService::getNodeId)
                    .thenComparing(RegisteredService::getServiceId));
        }
        for (List<CallPoint> value : tempType2CallMap.values()) {
            value.sort(Comparator.comparing(CallPoint::getPlatformId)
                    .thenComparing(CallPoint::getServerId)
                    .thenComparing(CallPoint::getNodeId)
                    .thenComparing(CallPoint::getServId));
        }

        type2ServiceMap = RuntimeUtils.convertModifyListMap(tempType2ServiceMap);
        callPoint2ServiceMap = Map.copyOf(tempCallPoint2ServiceMap);
        type2CallMap = RuntimeUtils.convertModifyListMap(tempType2CallMap);
    }

    private void notifyServiceEvent_nt(Collection<RegisteredService> serviceList,
                                        String eventName,
                                        BiConsumer<Service, Collection<RegisteredService>> callback) {
        if (serviceList.isEmpty()) {
            return;
        }
        List<RegisteredService> snapshot = List.copyOf(serviceList);
        for (Service value : services.values()) {
            try {
                value.postCoroutine(() -> callback.accept(value, snapshot));
            } catch (RuntimeException e) {
                LogCore.core.error("{} event rejected: service={}, serviceList={}",
                        eventName, value.getId(), snapshot, e);
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

    public RegisteredService getOfflineService(CallPoint callPoint) {
        return callPoint == null ? null : offlineServices.get(callPoint);
    }

    public ConcurrentHashMap<Object, Service> getServices() {
        return services;
    }

    public ConcurrentHashMap<CallPoint, List<RegisteredService>> getRemoteNodeServices() {
        return remoteNodeServices;
    }

    public CallPoint getCallPoint(String serviceId) {
        return getCallPoint(serviceId, nodeInfo.getNodeId());
    }

    public int getNodeId() {
        return nodeInfo.getNodeId();
    }

    private CallPoint getCallPoint(String serviceId, int nodeId) {
        return new CallPoint(platformId, serverId, nodeId, serviceId);
    }

    public CallPoint getNodeCallPoint() {
        return new CallPoint(nodeCallPoint);
    }

    public boolean isLocalNode(CallPoint callPoint) {
        return callPoint != null && nodeCallPoint.equals(callPoint.nodePoint());
    }

    private RemoteNode getRemoteNode(CallPoint callPoint) {
        return callPoint == null ? null : remoteNodes.get(callPoint.nodePoint());
    }

}


