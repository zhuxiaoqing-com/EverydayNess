package org.evd.game.runtime;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.evd.game.annotation.ServiceType;
import org.evd.game.runtime.call.*;
import org.evd.game.runtime.config.NodeInfo;
import org.evd.game.runtime.config.RegisteredService;
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
    /** 本帧需要在 Node 线程执行的投递事件。 */
    private final List<Runnable> affirmPostedTasks = new ArrayList<>();
//    /** ZMQ上下文 */
//    protected final ZContext zmqContext;
//    /** ZMQ连接 */
//    protected final ZMQ.Socket zmqPull;

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
                new BaseChannelInitializer(() -> new NodeChannelHandler(channelManager, this), false));
        LogCore.core.info("Netty 启动完成: node={}, port={}", getId(), port);

      /*  this.zmqContext = new ZContext();
        this.zmqPull = zmqContext.createSocket(SocketType.PULL);
        this.zmqPull.setLinger(3000);

        LogCore.core.info("节点【{}】绑定地址【{}】", name, addr);
        // 绑定到通用地址，这样通过内网和外网地址都可以连接上
        String addrWC = RegExUtils.replacePattern(addr, "\\d+.\\d+.\\d+.\\d+", "*");
        this.zmqPull.bind(addrWC);*/

        nodeExecutor = new ScheduledExecutor(name, 1);
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
        // 确认本次心跳要发送的remoteCall
        pulseAffirmRemoteCall_nt();
        pulseAffirmPostedTasks_nt();
        // 发送remoteCall
        pulseSendRemoteCall_nt();
        //pulseCallPuller_nt();
        pulsePostedTasks_nt();
        //调用远程Node的心跳操作
        pulseRemoteNodes_nt();
        // 本地服务注册变化后，广播给已连接节点
        pulseServiceRegistry_nt();
    }

    private void pulseServiceLifecycle_nt() {
        if (status != CaseStatus.Starting && status != CaseStatus.Running) {
            return;
        }

        // 运行状态下 所有的services都没了 就关服;
        if (services.isEmpty() && status == CaseStatus.Running) {
            stopForServiceTermination_nt(null, CaseStatus.Closed);
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
        stop(true);
    }

    private void pulseAffirmRemoteCall_nt() {
        // 本心跳要执行的call
        RemoteCall call;
        while ((call = remoteCalls.poll()) != null) {
            affirmRemoteCalls.add(call);
        }
    }

    private void pulseSendRemoteCall_nt() {
        for (RemoteCall call : affirmRemoteCalls) {
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
        if (id.equals(result.to.nodeId)) {
            Service sourceService = services.get(result.to.servId);
            if (sourceService == null) {
                LogCore.remote.error("local rpc rejection result cannot be delivered: node={}, targetService={}, waitId={}",
                        id, result.to.servId, result.id);
            } else {
                sourceService.addCall_snt(result);
            }
            return;
        }
        RemoteNode remoteNode = remoteNodes.get(result.to.nodeId);
        if (remoteNode == null) {
            LogCore.remote.error("remote rpc rejection result cannot be delivered: node={}, remoteNode={}, waitId={}",
                    id, result.to.nodeId, result.id);
            return;
        }
        remoteNode.sendCall(result);
    }

    /**
     * 发送RemoteCall
     * @param call
     */
    private void sendCall(RemoteCall call) {
        RemoteNode node = remoteNodes.get(call.getRemoteNodeId());
        if (node == null || !node.send(call.getPacket(), call.getExpectedChannelId())) {
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

        NetAcceptor currentAcceptor = acceptor;
        acceptor = null;
        if (currentAcceptor != null) {
            currentAcceptor.shutdown();
        }

        for (RemoteNode remoteNode : remoteNodes.values()) {
            remoteNode.close();
        }

        for (Service service : services.values()) {
            if (service.isStopping()) {
                continue;
            }
            service.postCoroutine(() -> service.stop(true));
        }
    }

    @Override
    protected void onClose() {
        remoteCalls.clear();
        postedTasks.clear();
        affirmRemoteCalls.clear();
        affirmPostedTasks.clear();
        remoteNodes.clear();
        remoteNodeServices.clear();
        channelManager.clear();

        // System.exit 会先执行 Bootstrap 注册的关闭钩子；此时不能提前关闭 Service 调度器，
        // 否则关闭钩子无法投递 Service.stop(true)。
        System.exit(0);
    }

    /**
     * 创建任务异步添加到service
     * @param service
     */
    public void addService(Service service){
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
     * @param nodeId 目标 Node
     * @param channelId 目标物理连接
     * @param buffer 序列化数据
     * @param bufferLength 有效数据长度
     */
    public boolean flushCall_st(String nodeId, long channelId, byte[] buffer, int bufferLength) {
        // 同一Node下 无需走传输协议 内部直接接收即可
        if (id.equals(nodeId)) {
            InputStream input = new InputStream(buffer, 0, bufferLength);
            localCallHandle_st(input);
            return true;
            // 其余的需要通过远程Node来发送请求值目标Node
        } else {
            RemoteNode remoteNode = remoteNodes.get(nodeId);
            if (remoteNode == null) {
                return false;
            }
            remoteCalls.add(new RemoteCall(nodeId, channelId, NodeFrameChunk.wrap(buffer, bufferLength)));
            return true;
        }
    }

    /**
     * 这里返回能发送，后面的所有不能发送都不再处理发送失败;
     */
    public long captureChannelId(CallBase call) {
        if (call == null || call.to == null || call.to.nodeId == null) {
            return -1L;
        }
        if (id.equals(call.to.nodeId)) {
            return 0L;
        }
        RemoteNode remoteNode = remoteNodes.get(call.to.nodeId);
        return remoteNode == null ? -1L : remoteNode.captureChannelId(call);
    }

    /** 仅检查目标 channel 的连接状态，用于检查已序列化但尚未满帧的数据。 */
    public boolean canSendOutboundConnection_nt(String nodeId, long channelId) {
        if (nodeId == null || channelId < 0L) {
            return false;
        }
        if (id.equals(nodeId)) {
            return channelId == 0L;
        }
        RemoteNode remoteNode = remoteNodes.get(nodeId);
        return remoteNode != null && remoteNode.isCurrentChannel(channelId);
    }

    /**
     * 处理Call请求
     */
    public void localCallHandle_st(InputStream input){

        // 是否已读取到末尾
        while (!input.isAtEnd()) {
            CallBase call = input.read();
            handleInboundCall(call);
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
        String remoteNodeId = sourceChannel == null ? null : sourceChannel.getChannel().attr(ServerAttributeKey.remoteNodeId).get();
        DebugPrint.printReceiveNodeFrame(getId(), remoteNodeId, sourceChannel, len);

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
                call.setSourceChannel(sourceChannel);
                post(() -> handleInboundCall(call));
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
        String remoteNodeId = channel.getChannel().attr(ServerAttributeKey.remoteNodeId).get();
        if (remoteNodeId == null) {
            return;
        }
        RemoteNode remoteNode = remoteNodes.get(remoteNodeId);
        if (remoteNode != null && remoteNode.onChannelInactive_nt(channel)) {
            remoteNodeServices.remove(remoteNode.getRemoteId());
            rebuildServiceIndexes();
        }
        failRpcWaitsForRemote_nt(remoteNodeId, channel.getChannelId());
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

    private void failRpcWaitsForRemote_nt(String remoteNodeId, long channelId) {
        for (Service service : services.values()) {
            if (service == null) {
                continue;
            }
            try {
                service.post(() -> {
                    int failed = service.failRpcWaitsForRemote(remoteNodeId, channelId);
                    if (failed > 0) {
                        LogCore.remote.warn("远程Node物理连接断开，结束对应连接RPC等待: localNode={}, remoteNode={}, channelId={}, service={}, count={}",
                                id, remoteNodeId, channelId, service.getId(), failed);
                    }
                });
            } catch (RuntimeException e) {
                LogCore.remote.error("远程Node断开时投递RPC等待清理失败: localNode={}, remoteNode={}, channelId={}, service={}",
                        id, remoteNodeId, channelId, service.getId(), e);
            }
        }
    }

    /**
     * 处理接收到的Call请求
     */
    public void callHandle_snt(CallBase call) {
        NetChannel sourceChannel = call.getSourceChannel();
        if (call.getFrom() == null || call.getTo() == null) {
            /*if (sourceChannel != null) {
                sourceChannel.close();
            }*/
            throw new SysException("rpc call point is missing: callType={}", call.getClass().getSimpleName());
        }
        if (!id.equals(call.getTo().nodeId)) {
            /*if (sourceChannel != null) {
                sourceChannel.close();
            }*/
            throw new SysException("rpc target node mismatch: localNode={}, targetNode={}, callType={}",
                    id, call.getTo().nodeId, call.getClass().getSimpleName());
        }

        //  sourceChannel==null,说明是同node的消息
        if (sourceChannel != null) {
            String attrRemoteNodeId = sourceChannel.getChannel().attr(ServerAttributeKey.remoteNodeId).get();
            if (attrRemoteNodeId == null && !(call instanceof CallNodeServicesSync)) {
                LogCore.remote.error("收到未完成远程节点握手的非法消息: node={}, callType={}, remoteNode={}",
                        getId(), call.getClass().getSimpleName(), call.from == null ? null : call.from.nodeId);
                sourceChannel.close();

            }
            if(attrRemoteNodeId != null && !attrRemoteNodeId.equals(call.from.nodeId)) {
                LogCore.remote.error("收到from.nodeId != attr.remoteId的非法消息: node={}, callType={}, from.remoteNode={}  attr.remoteId={}",
                        getId(), call.getClass().getSimpleName(), call.from == null ? null : call.from.nodeId, attrRemoteNodeId);
                return;
            }
        }

        DebugPrint.printReceiveRpc(call);

        String remoteId = call.from.nodeId;
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
                RemoteNode node = remoteNodes.get(remoteId);
                if (node == null) {
                    node = addRemoteNode(remoteId, callNodeServicesSync.getAddr());
                }
                if (callNodeServicesSync.isInit()) {
                    node.onNodeServicesSync_nt(sourceChannel);
                }
                syncRemoteServices_nt(remoteId, callNodeServicesSync.getServices());
            }
            break;
            case CallPing callPing: {
                onRemotePing_nt(remoteId, sourceChannel, callPing);
            }
            break;
            case CallPong callPong: {
                onRemotePong_nt(remoteId, sourceChannel, callPong);
            }
            break;
            default:
                throw new SysException("Unexpected call type: {}" + call.getClass());
        }
    }

    /**
     * 统一处理入站Call，负责把节点路由阶段的业务拒绝转换为RPC失败响应。
     */
    private void handleInboundCall(CallBase call) {
        try {
            callHandle_snt(call);
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



    private void onRemotePing_nt(String remoteNodeId, NetChannel sourceChannel, CallPing ping) {
        RemoteNode remoteNode = remoteNodes.get(remoteNodeId);
        if (remoteNode == null) {
            LogCore.remote.warn("收到未知远程node的ping: nodeId={}", remoteNodeId);
            return;
        }
        if (sourceChannel == null) {
            LogCore.remote.warn("收到远程node ping时连接不存在: nodeId={}", remoteNodeId);
            return;
        }
        remoteNode.updateServiceStatuses_nt(ping.getServiceStatuses());
        sourceChannel.setLastPingTime(currentTickTime_nt());

        CallPong call = new CallPong();
        call.from = new CallPoint(id, null);
        call.to = new CallPoint(remoteNodeId, null);
        call.setServiceStatuses(buildLocalServiceStatuses_nt());
        remoteNode.sendCall(call);
    }

    private void onRemotePong_nt(String remoteNodeId, NetChannel sourceChannel, CallPong pong) {
        RemoteNode remoteNode = remoteNodes.get(remoteNodeId);
        if (remoteNode == null) {
            LogCore.remote.warn("收到未知远程node的pong: nodeId={}", remoteNodeId);
            return;
        }
        remoteNode.updateServiceStatuses_nt(pong.getServiceStatuses());
        sourceChannel.setLastPingTime(currentTickTime_nt());
    }


    private void pulseInboundRemoteChannelsTimeout_nt() {
        long timeCurr = currentTickTime_nt();
        for (NetChannel netChannel : channelManager.snapshotChannels()) {
            String remoteNodeId = netChannel.getChannel().attr(ServerAttributeKey.remoteNodeId).get();

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
                try {
                    value.postCoroutine(() -> value.onServiceConnect(addList));
                } catch (RuntimeException e) {
                    LogCore.core.error("service connect event rejected: service={}, addList={}",
                            value.getId(), addList, e);
                }
            }

            if (!removeList.isEmpty()) {
                try {
                    value.postCoroutine(() -> value.onServiceDisconnect(removeList));
                } catch (RuntimeException e) {
                    LogCore.core.error("service disconnect event rejected: service={}, removeList={}",
                            value.getId(), removeList, e);
                }
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

    public ConcurrentHashMap<String, List<RegisteredService>> getRemoteNodeServices() {
        return remoteNodeServices;
    }
}


