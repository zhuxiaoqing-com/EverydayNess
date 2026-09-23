package org.evd.game.runtime;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallServiceInitDataSync;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 当前 Service 对其他 Service 的连接、初始化同步状态和可访问索引。 */
@Slf4j
final class ServicePeerRegistry {
    private static final long INIT_DATA_SYNC_CHECK_INTERVAL_MILLIS = 1_000L;
    private static final long INIT_DATA_SYNC_TIMEOUT_MILLIS = 30_000L;
    private static final long INIT_DATA_SYNC_LOG_INTERVAL_MILLIS = 60_000L;

    private final Service service;
    private final Node node;
    /** 只在所属 Service 线程修改。 */
    private final Map<CallPoint, OtherServiceInfo> otherServiceMap = new HashMap<>();
    /** 只包含已完成对方全量数据同步的 Service。 */
    private volatile Map<ServiceType, List<RegisteredService>> type2ServiceMap = Map.of();
    private volatile Map<ServiceType, List<CallPoint>> type2CallMap = Map.of();

    /**
     * 创建指定 Service 的其他 Service 状态注册表。
     *
     * @param service 状态归属的本地 Service
     */
    ServicePeerRegistry(Service service) {
        this.service = service;
        this.node = service.getNode();
    }

    /** 启动初始化数据同步超时日志检查。 */
    void start() {
        service.newRepeatedTimerCoroutine(
                INIT_DATA_SYNC_CHECK_INTERVAL_MILLIS,
                false,
                this::checkInitDataSyncTimeout);
    }

    /**
     * 处理 Node 发现的 Service 连接或重新连接，并刷新连接代次缓存。
     *
     * @param serviceList Node 当前发现的 Service 快照
     */
    void onServiceConnect(Collection<RegisteredService> serviceList) {
        long now = serviceTime();
        for (RegisteredService registeredService : serviceList) {
            if (isLocalOrInvalidService(registeredService)) continue;
            OtherServiceInfo newInfo = new OtherServiceInfo(new RegisteredService(registeredService), now);
            OtherServiceInfo oldInfo = otherServiceMap.put(registeredService.getCallPoint(), newInfo);
            if (oldInfo != null) {
                log.info("替换其他Service连接缓存: service={}, otherService={}, oldService={}, newService={}",
                        service.getId(), registeredService.getCallPoint(),
                        oldInfo.getService(), newInfo.getService());
            }
        }
        rebuildServiceIndexes();
    }

    List<RegisteredService> getServicesByType(ServiceType serviceType) {
        if (serviceType == null) {
            return List.of();
        }
        List<RegisteredService> peers = type2ServiceMap.getOrDefault(serviceType, List.of());
        List<RegisteredService> currentPeers = new ArrayList<>(peers.size());
        for (RegisteredService peer : peers) {
            if (isCurrentSyncedPeer(peer.getCallPoint())) {
                currentPeers.add(new RegisteredService(peer));
            }
        }
        return List.copyOf(currentPeers);
    }

    List<CallPoint> getCallPointByType(ServiceType serviceType) {
        if (serviceType == null) {
            return List.of();
        }
        List<CallPoint> callPoints = type2CallMap.getOrDefault(serviceType, List.of());
        List<CallPoint> currentCallPoints = new ArrayList<>(callPoints.size());
        for (CallPoint callPoint : callPoints) {
            if (isCurrentSyncedPeer(callPoint)) {
                currentCallPoints.add(new CallPoint(callPoint));
            }
        }
        return List.copyOf(currentCallPoints);
    }

    CallPoint getAnyCallPointByType(ServiceType serviceType) {
        List<RegisteredService> registeredServices = getServicesByType(serviceType);
        return registeredServices.isEmpty() ? null : registeredServices.getFirst().getCallPoint();
    }

    /** 初始化或增量数据同步使用；普通业务路由必须使用 initData 索引。 */
    CallPoint getAnyInitDataSyncCallPointByType(ServiceType serviceType) {
        if (serviceType == null) {
            return null;
        }
        RegisteredService selectedPeer = null;
        for (OtherServiceInfo info : otherServiceMap.values()) {
            RegisteredService peer = info.getService();
            if (peer.getServiceType() != serviceType || !isCurrentPeer(peer)) {
                continue;
            }
            if (selectedPeer == null || compareService(peer, selectedPeer) < 0) {
                selectedPeer = peer;
            }
        }
        return selectedPeer == null ? null : new CallPoint(selectedPeer.getCallPoint());
    }

    private boolean isCurrentSyncedPeer(CallPoint callPoint) {
        OtherServiceInfo info = otherServiceMap.get(callPoint);
        return info != null && info.isInitDataSync() && isCurrentPeer(info.getService());
    }

    private boolean isCurrentPeer(RegisteredService peer) {
        RegisteredService currentPeer = node.getRegisteredService(peer.getCallPoint());
        return currentPeer != null && !peer.isDifferentServiceSession(currentPeer);
    }

    private int compareService(RegisteredService first, RegisteredService second) {
        int nodeOrder = Integer.compare(first.getNodeId(), second.getNodeId());
        return nodeOrder != 0 ? nodeOrder : first.getServiceId().compareTo(second.getServiceId());
    }

    private void rebuildServiceIndexes() {
        Map<ServiceType, List<RegisteredService>> servicesByType = new HashMap<>();
        Map<ServiceType, List<CallPoint>> callPointsByType = new HashMap<>();
        for (OtherServiceInfo info : otherServiceMap.values()) {
            if (!info.isInitDataSync()) {
                continue;
            }
            RegisteredService peer = new RegisteredService(info.getService());
            servicesByType.computeIfAbsent(peer.getServiceType(), ignored -> new ArrayList<>()).add(peer);
            callPointsByType.computeIfAbsent(peer.getServiceType(), ignored -> new ArrayList<>())
                    .add(peer.getCallPoint());
        }

        for (List<RegisteredService> peers : servicesByType.values()) {
            peers.sort(Comparator.comparing(RegisteredService::getNodeId)
                    .thenComparing(RegisteredService::getServiceId));
        }
        for (List<CallPoint> callPoints : callPointsByType.values()) {
            callPoints.sort(Comparator.comparing(CallPoint::getPlatformId)
                    .thenComparing(CallPoint::getServerId)
                    .thenComparing(CallPoint::getNodeId)
                    .thenComparing(CallPoint::getServId));
        }

        servicesByType.replaceAll((serviceType, peers) -> List.copyOf(peers));
        callPointsByType.replaceAll((serviceType, callPoints) -> List.copyOf(callPoints));
        type2ServiceMap = servicesByType;
        type2CallMap = callPointsByType;
    }

    /**
     * 发送当前 Service 的初始化数据完成通知。业务层在此前的
     * {@code onServiceConnect} 中发送的快照/增量消息，会和这个标记保持同一出站顺序。
     *
     * @param serviceList 本轮新发现或重新连接的 Service
     */
    void sendInitDataSync(Collection<RegisteredService> serviceList) {
        for (RegisteredService registeredService : serviceList) {
            if (isLocalOrInvalidService(registeredService)) continue;
            OtherServiceInfo info = otherServiceMap.get(registeredService.getCallPoint());
            if (info == null) {
                continue;
            }
            if (info.getService().isDifferentServiceSession(registeredService)) {
                log.error("发送其他Service初始化同步通知时连接代次不一致: service={}, otherService={}, cachedService={}, targetService={}",
                        service.getId(), registeredService.getCallPoint(), info.getService(), registeredService);
                continue;
            }
            CallServiceInitDataSync call = CallFactory.buildServiceInitDataSync(info.getService());

            RpcResult<Void> result = RpcResult.run(() -> service.sendOutboundCall(call));
            if (!result.isSuccess()) {
                log.error("发送其他Service初始化同步通知失败: service={}, otherService={}, sessionId={}, errorCode={}, message={}",
                        service.getId(), info.getService().getCallPoint(), info.getSessionId(),
                        result.getErrorCode(), result.getErrorMessage());
            }
        }
    }

    /**
     * 处理 Node 发现的 Service 下线，并只移除仍匹配当前连接代次的缓存。
     *
     * @param serviceList 从 Node 最新快照中消失的 Service
     */
    void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        boolean changed = false;
        for (RegisteredService registeredService : serviceList) {
            if (isLocalOrInvalidService(registeredService)) continue;
            CallPoint callPoint = registeredService.getCallPoint();
            OtherServiceInfo info = otherServiceMap.get(callPoint);
            if (info == null) {
                continue;
            }
            if (info.getService().isDifferentServiceSession(registeredService)) {
                log.error("断开其他Service时连接代次不一致: service={}, otherService={}, cachedService={}, disconnectService={}",
                        service.getId(), callPoint, info.getService(), registeredService);
                continue;
            }
            changed |= otherServiceMap.remove(callPoint, info);
        }
        if (changed) {
            rebuildServiceIndexes();
        }
    }

    /**
     * 应用其他 Service 发来的初始化数据同步通知。
     * 过期连接、过期 Service 实例或目标不匹配的通知会被丢弃。
     *
     * @param call 初始化数据同步通知
     */
    void onInitDataSync(CallServiceInitDataSync call) {
        OtherServiceInfo info = otherServiceMap.get(call.getFrom());
        if (!checkSyncValid(call, "初始化数据同步通知")) {
            return;
        }

        if (!info.isInitDataSync()) {
            info.setInitDataSync(true);
            rebuildServiceIndexes();
            service._onServiceInitDataSync(info.getService());
        }
    }

    public boolean checkSyncValid(CallServiceInitDataSync call, String reason) {
        CallPoint sourceCallPoint = call.getFrom();
        // 来源实例和连接代次必须仍与本地缓存一致。
        OtherServiceInfo info = otherServiceMap.get(sourceCallPoint);
        if(isCurrentSync(call, info)) {
           return true; 
        }
        log.error("收到过期或身份不匹配的{}: service={}, from={}, target={}, current={}, call={}",
                reason,
                service.getId(),
                call.getFrom(),
                call.getTargetService(),
                info == null ? null : info.getService(),
                call);
        
        return false;
    }
    /**
     * 校验初始化同步通知的来源仍是缓存中的连接，目标仍是本地 Service 当前实例，
     * 且 Node 记录的来源会话仍与通知目标一致。
     *
     * @param call 初始化数据同步通知
     * @return 通知是否属于当前连接代次
     */
    private boolean isCurrentSync(CallServiceInitDataSync call, OtherServiceInfo info ) {
        if (call == null) {
            return false;
        }

        CallPoint sourceCallPoint = call.getFrom();
        // 来源实例和连接代次必须仍与本地缓存一致。
        if (info == null) {
            return false;
        }

        RegisteredService source = info.getService();
        if (source == null) {
            return false;
        }
        if (source.getServiceInstanceId() != call.getSourceServiceInstanceId()) {
            return false;
        }
        if (source.getSessionId() != call.getSourceSessionId()) {
            return false;
        }

        // 通知目标必须是当前本地 Service 的同一实例。
        RegisteredService target = call.getTargetService();
        if (target == null) {
            return false;
        }

        CallPoint targetCallPoint = target.getCallPoint();
        if (targetCallPoint == null) {
            return false;
        }

        CallPoint localCallPoint = service.getCallPoint();
        if (localCallPoint == null) {
            return false;
        }
        if (!localCallPoint.equals(targetCallPoint)) {
            return false;
        }
        if (service.getServiceInstanceId() != target.getServiceInstanceId()) {
            return false;
        }

        // Node 记录的来源远程会话必须仍与通知中的目标会话一致。
        if (node.getRemoteSessionId(sourceCallPoint) != target.getSessionId()) {
            return false;
        }
        return true;
    }

    /**
     * 判断注册项是否应被当前 Service 的其他 Service 状态表忽略。
     * 当前 Service 自身会包含在 Node 的 Service 快照中，因此不能加入其他 Service 表。
     *
     * @param registeredService 待判断的 Service 注册项
     * @return 注册项为空、CallPoint 无效或属于当前 Service 时返回 true
     */
    private boolean isLocalOrInvalidService(RegisteredService registeredService) {
        return registeredService == null
                || registeredService.getCallPoint() == null
                || service.getCallPoint().equals(registeredService.getCallPoint());
    }

    /** 执行一次初始化数据同步超时检查。 */
    private void checkInitDataSyncTimeout() {
        long now = serviceTime();
        checkInitDataSync(now);
    }

    /**
     * 检查长时间未完成初始化数据同步的其他 Service，并按间隔记录日志。
     *
     * @param now 当前 Service 时间
     */
    private void checkInitDataSync(long now) {
        for (OtherServiceInfo info : otherServiceMap.values()) {
            if (info.isInitDataSync()
                    || now - info.getConnectTime() <= INIT_DATA_SYNC_TIMEOUT_MILLIS) {
                continue;
            }
            if (info.getLastErrorLogTime() > 0L
                    && now - info.getLastErrorLogTime() < INIT_DATA_SYNC_LOG_INTERVAL_MILLIS) {
                continue;
            }
            info.setLastErrorLogTime(now);
            log.error(
                    "其他Service初始化数据未同步完成: service={}, otherService={}, connectTime={}, sessionId={}, initDataSync={}",
                    service.getId(),
                    info.getService().getCallPoint(),
                    info.getConnectTime(),
                    info.getSessionId(),
                    info.isInitDataSync());
        }
    }

    /** 返回 Service 的逻辑时间；逻辑时间不可用时回退到系统时间。 */
    private long serviceTime() {
        long now = service.getTimeCurrent();
        return now > 0L ? now : System.currentTimeMillis();
    }
}
