package org.evd.game.runtime;

import org.evd.game.runtime.call.CallFactory;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallServiceInitDataSync;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.ymlconfig.RegisteredService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Service 对其他 Service 的连接、路由 Ready 和初始化同步状态代理。 */
final class OtherServiceRegistry {
    private static final long INIT_DATA_SYNC_CHECK_INTERVAL_MILLIS = 1_000L;
    private static final long INIT_DATA_SYNC_TIMEOUT_MILLIS = 30_000L;
    private static final long INIT_DATA_SYNC_LOG_INTERVAL_MILLIS = 60_000L;

    /** 新发现的 Service 进入通信 Ready 前的稳定等待时间。 */
    static final long SERVICE_PENDING_TIME = 10_000L;

    private final Service service;
    private final Node node;
    /** 只在所属 Service 线程修改。 */
    private final Map<CallPoint, OtherServiceInfo> otherServiceMap = new HashMap<>();

    /**
     * 创建指定 Service 的其他 Service 状态注册表。
     *
     * @param service 状态归属的本地 Service
     */
    OtherServiceRegistry(Service service) {
        this.service = service;
        this.node = service.getNode();
    }

    /** 启动定时检查，用于推进连接 Ready 状态并检查初始化数据同步超时。 */
    void start() {
        // 定时器回调也必须回到 Service 队列，避免与连接/同步消息并发修改状态。
        service.newRepeatedTimerCoroutine(
                INIT_DATA_SYNC_CHECK_INTERVAL_MILLIS,
                false,
        this::checkServiceState);
    }

    /** 返回当前 Service 维护的其他 Service 状态表。 */
    Map<CallPoint, OtherServiceInfo> getOtherServiceMap() {
        return otherServiceMap;
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
                LogCore.core.info("替换其他Service连接缓存: service={}, otherService={}, oldService={}, newService={}",
                        service.getId(), registeredService.getCallPoint(),
                        oldInfo.getService(), newInfo.getService());
            }
        }
    }

    /**
     * 发送当前 Service 的初始化完成通知。业务层在此前的
     * {@code onServiceConnectReady} 中发送的快照/增量消息，会和这个标记保持同一出站顺序。
     *
     * @param serviceList 已经进入通信 Ready 状态的 Service
     */
    void sendInitDataSync(Collection<RegisteredService> serviceList) {
        for (RegisteredService registeredService : serviceList) {
            if (isLocalOrInvalidService(registeredService)) continue;
            OtherServiceInfo info = otherServiceMap.get(registeredService.getCallPoint());
            if (info == null) {
                continue;
            }
            if (info.getService().isDifferentServiceSession(registeredService)) {
                LogCore.core.error("发送其他Service初始化同步通知时连接代次不一致: service={}, otherService={}, cachedService={}, readyService={}",
                        service.getId(), registeredService.getCallPoint(), info.getService(), registeredService);
                continue;
            }
            CallServiceInitDataSync call = CallFactory.buildServiceInitDataSync(info.getService());

            RpcResult<Void> result = RpcResult.run(() -> service.sendOutboundCall(call));
            if (!result.isSuccess()) {
                LogCore.core.error("发送其他Service初始化同步通知失败: service={}, otherService={}, sessionId={}, errorCode={}, message={}",
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
        for (RegisteredService registeredService : serviceList) {
            if (isLocalOrInvalidService(registeredService)) continue;
            CallPoint callPoint = registeredService.getCallPoint();
            OtherServiceInfo info = otherServiceMap.get(callPoint);
            if (info == null) {
                continue;
            }
            if (info.getService().isDifferentServiceSession(registeredService)) {
                LogCore.core.error("断开其他Service时连接代次不一致: service={}, otherService={}, cachedService={}, disconnectService={}",
                        service.getId(), callPoint, info.getService(), registeredService);
                continue;
            }
            otherServiceMap.remove(callPoint, info);
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
        if (!isCurrentSync(call, info)) {
            LogCore.core.error("收到过期或身份不匹配的{}: service={}, from={}, target={}, current={}, call={}",
                    "初始化数据同步通知",
                    service.getId(),
                    call.getFrom(),
                    call.getTargetService(),
                    info == null ? null : info.getService(),
                    call);
            return;
        }

        try {
            if (!info.isInitDataSync()) {
                service.onServiceInitDataSync(info.getService());
                info.setInitDataSync(true);
            }
        } catch (RuntimeException e) {
            LogCore.core.error("应用其他Service初始化数据失败: service={}, otherService={}, sessionId={}",
                    service.getId(), info.getService().getCallPoint(), info.getSessionId(), e);
        }
    }

    /**
     * 校验初始化同步通知的来源 Service、目标 Service 和目标 Node 连接是否仍然有效。
     *
     * @param call 初始化数据同步通知
     * @param info 当前缓存的来源 Service 状态
     * @return 通知是否属于当前连接代次
     */
    private boolean isCurrentSync(CallServiceInitDataSync call, OtherServiceInfo info) {
        if (info == null) {
            return false;
        }
        RegisteredService source = info.getService();
        RegisteredService target = call.getTargetService();
        return source.getServiceInstanceId() == call.getSourceServiceInstanceId()
                && source.getSessionId() == call.getSourceSessionId()
                && target != null
                && service.getCallPoint().equals(target.getCallPoint())
                && service.getServiceInstanceId() == target.getServiceInstanceId()
                && node.getRemoteSessionId(call.getFrom()) == target.getSessionId();
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

    /** 执行一次连接状态和初始化数据同步状态检查。 */
    private void checkServiceState() {
        long now = serviceTime();
        checkServiceConnectReady(now);
        checkInitDataSync(now);
    }

    /**
     * 将达到稳定等待时间的其他 Service 标记为通信 Ready，并通知本地 Service。
     *
     * @param now 当前 Service 时间
     */
    private void checkServiceConnectReady(long now) {
        List<RegisteredService> readyServices = new ArrayList<>();
        for (OtherServiceInfo info : otherServiceMap.values()) {
            if (info.isReady()
                    || now - info.getConnectTime() < SERVICE_PENDING_TIME) {
                continue;
            }
            info.setReady(true);
            readyServices.add(info.getService());
        }
        if (readyServices.isEmpty()) {
            return;
        }
        try {
            service.onServiceConnectReady_nt(readyServices);
        } catch (RuntimeException e) {
            LogCore.core.error("Service通信Ready回调失败: service={}, otherServices={}",
                    service.getId(), readyServices, e);
        }
    }

    /**
     * 检查已经 Ready 但长时间未完成初始化数据同步的其他 Service，并按间隔记录日志。
     *
     * @param now 当前 Service 时间
     */
    private void checkInitDataSync(long now) {
        for (OtherServiceInfo info : otherServiceMap.values()) {
            if (info.isReadyAndSync()
                    || now - info.getConnectTime() <= INIT_DATA_SYNC_TIMEOUT_MILLIS) {
                continue;
            }
            if (info.getLastErrorLogTime() > 0L
                    && now - info.getLastErrorLogTime() < INIT_DATA_SYNC_LOG_INTERVAL_MILLIS) {
                continue;
            }
            info.setLastErrorLogTime(now);
            LogCore.core.error(
                    "其他Service初始化数据未同步完成: service={}, otherService={}, connectTime={}, sessionId={}, ready={}, " +
                            "initDataSync={}",
                    service.getId(),
                    info.getService().getCallPoint(),
                    info.getConnectTime(),
                    info.getSessionId(),
                    info.isReady(),
                    info.isInitDataSync());
        }
    }

    /** 返回 Service 的逻辑时间；逻辑时间不可用时回退到系统时间。 */
    private long serviceTime() {
        long now = service.getTimeCurrent();
        return now > 0L ? now : System.currentTimeMillis();
    }
}
