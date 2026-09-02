package org.evd.game.runtime;

import jdk.internal.vm.ContinuationScope;
import org.evd.game.annotation.service.ServiceName;
import org.apache.logging.log4j.ThreadContext;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.Db.table.Mdb;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorMailBoxRegistry;
import org.evd.game.runtime.actorLogic.ActorInterfaceIndexer;
import org.evd.game.runtime.actorLogic.ActorManager;
import org.evd.game.runtime.actorLogic.EventListenerInterfaceProcessor;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallResult;
import org.evd.game.runtime.call.RpcCallBase;
import org.evd.game.runtime.config.ConfigTableInitializer;
import org.evd.game.runtime.annotation.Event;
import org.evd.game.runtime.annotation.EventListener;
import org.evd.game.runtime.ymlconfig.GlobalYml;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.ymlconfig.ServiceInfo;
import org.evd.game.runtime.continuation.*;
import org.evd.game.runtime.mailbox.MailBoxBean;
import org.evd.game.runtime.mailbox.MessageLocationSender;
import org.evd.game.runtime.mailbox.ProcessInnerSender;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;
import org.evd.game.runtime.rpcProxyInterface.RpcInboundDispatcher;
import org.evd.game.runtime.rpcProxyInterface.RpcMethodInvoker;
import org.evd.game.runtime.rpcProxyInterface.RpcOutboundGateway;
import org.evd.game.runtime.serializeBean.Chunk;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.RpcCallException;
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.util.DeadlineTimerWheelScheduler;
import org.evd.game.runtime.util.TimerScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 服务
 */
public class Service extends TickCase {
    public void addCall_snt(CallBase call) {
        if (call == null) {
            throw new SysException("service call is null: service={}", id);
        }
        calls.add(call);
    }

    enum ServiceStatus {
        New,
        Running,
        PendingKill,
        Closed
    }

    /**
     * node
     */
    protected final Node node;

    public Node getNode() {
        return node;
    }

    public ServiceInfo serviceInfo;

    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    public ServiceType getServiceType() {
        return serviceInfo.getServiceType();
    }

    /**
     * 线程池名字
     */
    private final String scheduledName;

    public String getScheduledName() {
        return scheduledName;
    }

    /**
     * service的接收队列
     */
    private final FrameQueue<CallBase> calls = new FrameQueue<>(new ConcurrentLinkedDeque<>());
    /**
     * 此帧要执行的calls
     */
    private final List<CallBase> affirmCalls = new ArrayList<>();
    /**
     * 非 service 线程投递过来的任务
     */
    private final FrameQueue<Runnable> postedTasks = new FrameQueue<>(new ConcurrentLinkedDeque<>());
    /**
     * 此帧要执行的投递任务
     */
    private final List<Runnable> affirmPostedTasks = new ArrayList<>();
    /** 由 Service 线程按帧发布，供 Node 跨线程读取。 */
    private volatile PressureSnapshot pressureSnapshot = PressureSnapshot.EMPTY;
    /**
     * 协程的组，与service同名
     */
    private final ContinuationScope scope;

    public ContinuationScope getScope() {
        return scope;
    }

    /**
     * 当前 service 内的 actor mailbox 注册表
     */
    private final ActorMailBoxRegistry actorMailBoxRegistry = new ActorMailBoxRegistry(this);
    /**
     * 通用定时调度器
     */
    private final TimerScheduler timerScheduler = new DeadlineTimerWheelScheduler(
            (timerId, failure) -> LogCore.core.error(
                    "service timer callback failed: service={}, timerId={}", id, timerId, failure));
    /**
     * continuation 调度与 wait/timeout
     */
    private final ContinuationRuntime continuationRuntime = new ContinuationRuntime(
            this,
            this::onContinuationComplete);
    /**
     * 通用协程锁
     */
    private final CoroutineLockManager coroutineLockManager = new CoroutineLockManager(
            timerScheduler,
            continuationRuntime,
            continuationRuntime::requireRunning,
            this::getWaitBaseTimeInternal,
            id);
    /**
     * actor mailbox 分发
     */
    private final ProcessInnerSender processInnerSender;
    /**
     * 已知 actor address 的 message sender
     */
    private final MessageSender messageSender;
    /**
     * rpc 方法装配与调用
     */
    private final RpcMethodInvoker rpcMethodInvoker = new RpcMethodInvoker(this);
    /**
     * actor 实例管理器
     */
    private ActorManager actorManager;
    /**
     * actor 直接接口索引
     */
    private ActorInterfaceIndexer actorInterfaceIndexer;
    /**
     * actor location 查询、缓存与投递
     */
    private MessageLocationSender messageLocationSender;
    /**
     * call transport 与发送缓冲
     */
    private final CallTransport callTransport;
    /**
     * rpc 出站网关
     */
    private final RpcOutboundGateway rpcOutboundGateway;
    /**
     * rpc 入站分发
     */
    private final RpcInboundDispatcher rpcInboundDispatcher;
    /**
     * ThreadLocal
     */
    private final static ThreadLocal<Service> threadLocal = new ThreadLocal<>();

    public static Service getCurrent() {
        return threadLocal.get();
    }

    public static <T extends Service> T getCurrent(Class<T> serviceType) {
        Service current = threadLocal.get();
        if (current == null) {
            return null;
        }
        return serviceType.cast(current);
    }

    /**
     * 本service的调用点
     */
    private final CallPoint callPoint;
    Mdb mdb;

    public Service(Node node, String name, String scheduledName, long tickInterval, ServiceInfo serviceInfo) {
        super(name, tickInterval);
        this.node = node;
        this.scheduledName = scheduledName;
        this.scope = new ContinuationScope(name);
        this.callPoint = node.getCallPoint(name);
        this.callTransport = new CallTransport(node, this, timerScheduler);
        this.messageSender = new MessageSender(this);
        this.processInnerSender = new ProcessInnerSender(this);
        this.rpcOutboundGateway = new RpcOutboundGateway(this);
        this.rpcInboundDispatcher = new RpcInboundDispatcher(this);
        this.serviceInfo = serviceInfo;
        initActorManagerIfPresent();


        if (supportLocation()) this.messageLocationSender = new MessageLocationSender(this);
    }

    @Override
    protected void init_t() {
        threadLocal.set(this);
        try {
            // 先执行初始化
            initVirtual_t();
        } finally {
            threadLocal.remove();
        }
    }

    /**
     * Service 启动后立即登记到 Node，随后才开始异步初始化。
     * Node 通过心跳观察该 Service 的完整状态变化。
     */
    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * init由协程执行，交给子类继承
     */
    @Override
    public final void _init() {
        super._init();
        if (messageLocationSender != null) {
            newRepeatedTimer(
                    MessageLocationSender.getCleanupIntervalMillis(),
                    false,
                    messageLocationSender::cleanupIdle);
        }

        if (supportTable()) {
            ConfigTableInitializer.init(GlobalYml.requireTableDir());
        }

        if (requiresMdb()) {
            this.mdb = new Mdb();
            DBExecInterface dbExecutor = node.hasNodeDbExecutor()
                    ? node.getNodeDbExecutor()
                    : (DBExecInterface) ServiceName.getRpcProxyObj(ServiceName.DB_SERVICE);
            mdb.start(getClass(), dbExecutor, this);
        }


        /*
         * 不能进行rpc操作;只能本地初始化一些数据;
         * 而且一般rpc操作也都是要等onServiceConnect上来以后才进行的吧; 直接rpc也没service给你访问呀;
         */
        init();

        // 直接在这里标识,目前看没什么问题
        node.attachToNode(this);
        /*
         * 这里和下面的publishService是一体的,
         * 和addService分开是为了 init()里可以进行rpc操作，会等rpc操作完成以后，才会发布service;
         * 但是这里没法接到return，其实用处不大;所以不需要init可以进行rpc，直接不允许rpc就好了;
         * 至于下面的逻辑 继续保持这样也没事，就让init结束时才进行标记运行;
         */
        markRunning();
        // init 可能挂起；只有完整成功返回后，服务才对本地和远端路由可见。
        node.publishService(this);
    }

    /**
     * 返回当前 Service 是否实际需要 MDB，同时供启动阶段统计 NODE_LOCAL 的数据库使用者。
     */
    public final boolean requiresMdb() {
        return supportMdb();
    }

    public void init() {

    }


    /**
     * init方法交给协程执行
     * 因为init中可能存在异步操作，异步可能触发协程yield，导致线程yield
     */
    private void initVirtual_t() {
        continuationRuntime.createAndRun(() -> {
            try {
                _init();
            } catch (Throwable e) {
                if (e instanceof VirtualMachineError virtualMachineError) {
                    throw virtualMachineError;
                }
                status = CaseStatus.FinishKill;
                LogCore.core.error("service initialization failed: service={}, class={}",
                        id, getClass().getName(), e);
            }
        }, null);
    }

    @Override
    protected void pulse() {
        // service放到threadLocal，以便于逻辑中从当前上线文中获取
        threadLocal.set(this);
        ThreadContext.put("service", getId());
        try {
            pulseAffirm_st();

            pulseCalls_st();
            // 先处理已经进入 Service 的入站结果，再处理断链等 posted 事件。
            // 这样同一帧已到达的 CallResult 不会被断链清理抢先结束。
            pulsePostedTasks_st();
            tick_st();

            pulseTask_st();
            pulseEntity_st();

            drainQueuedContinuations_st();

            //刷新call发送缓冲区
            flushCallFrameBuffers_st();
        } finally {
            publishPressureSnapshot_st();
            // 逻辑结束后移除，因为下次tick会分配其他线程
            threadLocal.remove();
            ThreadContext.remove("service");
        }
    }

    /**
     * tick保持同步驱动。
     * 需要等待RPC/定时器的逻辑，应该显式启动独立业务协程，而不是阻塞tick本身。
     */
    private void tick_st() {
        tick();
        if (mdb != null) {
            mdb.tick(getTime());
        }
    }

    public void tick() {
    }

    /**
     * 返回rpc同步等待的默认超时时间，单位毫秒
     * 小于等于0代表不启用超时
     */
    protected long getCallWaitTimeout() {
        return 10_000;
    }

    public long getCallWaitTimeoutInternal() {
        return getCallWaitTimeout();
    }

    private long getWaitBaseTime() {
        long now = getTimeCurrent();
        return now > 0 ? now : System.currentTimeMillis();
    }

    // 运行时协作对象统一从 Service 取上下文，避免构造参数层层透传。
    public ActorMailBoxRegistry actorMailBoxRegistry() {
        return actorMailBoxRegistry;
    }

    public ContinuationRuntime continuationRuntime() {
        return continuationRuntime;
    }

    /** 返回上一帧发布的可运行积压；wait 中的 continuation 不包含在内。 */
    public int readyContinuationsSize() {
        return pressureSnapshot.readyContinuations();
    }

    public PressureSnapshot pressureSnapshot() {
        return pressureSnapshot;
    }

    public CoroutineLockManager coroutineLockManagerInternal() {
        return coroutineLockManager;
    }

    public void sendOutboundCall(CallBase call) {
        callTransport.send(call);
    }

    public CallTransport getCallTransport() {
        return callTransport;
    }

    public boolean handleRpcResult(CallResult callResult) {
        if (callResult.success) {
            return callTransport.completePendingRpc(callResult);
        }
        return callTransport.failPendingRpc(
                callResult,
                new RpcCallException(
                        callResult.errorCode,
                        "rpc call failed: service=" + id + ", waitId=" + callResult.id
                                + ", methodKey=" + callResult.methodKey
                                + ", errorCode=" + callResult.errorCode + ", message=" + callResult.errorMessage));
    }

    /** 仅在 Service 线程处理入站 RPC 结果，保持其与断链任务的 FIFO 顺序。 */
    void handleInboundResult_st(CallResult callResult) {
        rpcInboundDispatcher.handle(callResult);
    }

    int failRpcWaitsForRemote(CallPoint remoteNodePoint, long sessionId) {
        callTransport.discard(remoteNodePoint, sessionId);
        return callTransport.failPendingRpcForSession(sessionId);
    }

    public CallPoint getCallPoint() {
        return callPoint;
    }

    public long getWaitBaseTimeInternal() {
        return getWaitBaseTime();
    }

    public ProcessInnerSender getProcessInnerSender() {
        return processInnerSender;
    }

    public RpcMethodInvoker getRpcMethodInvoker() {
        return rpcMethodInvoker;
    }

    private void initActorManagerIfPresent() {
        try {
            if (actorManager != null) {
                return;
            }
            Class<?> cls;
            try {
                cls = Class.forName(getClass().getName() + "ActorManager");
            } catch (ClassNotFoundException ignored) {
                return;
            }
            actorManager = (ActorManager) cls.getDeclaredConstructor().newInstance();
            actorInterfaceIndexer = new ActorInterfaceIndexer(actorManager.getActors());
            // 事件进行依赖处理
            new EventListenerInterfaceProcessor().process(actorInterfaceIndexer);
        } catch (Exception e) {
            throw new SysException(e, "初始化 ActorManager 失败: service={}", id);
        }
    }

    private ActorManager actorManager() {
        if (actorManager == null) {
            throw new SysException("未找到 ActorManager: service={}", id);
        }
        return actorManager;
    }


    public final <T> T getActor(Class<T> actorType) {
        return actorManager().getActor(actorType);
    }

    public final Map<Class<?>, Object> getActorMap() {
        return actorManager().getActors();
    }

    /**
     * 通过接口获取实现了该结构的所有Actor
     */
    public final <T> Collection<T> getActorByInterface(Class<T> actorInterface) {
        if (actorInterface == null) {
            throw new SysException("actorInterface is null: service={}", id);
        }
        return actorInterfaceIndexer.getObjByClass(actorInterface);
    }

    public final <T> void forEachActor(Class<T> actorInterface, Consumer<T> consumer) {
        if (actorInterface == null) {
            throw new SysException("actorInterface is null: service={}", id);
        }
        List<T> objByClass = actorInterfaceIndexer.getObjByClass(actorInterface);
        for (T byClass : objByClass) {
            try {
                consumer.accept(byClass);
            } catch (Exception e) {
                LogCore.core.error("Service Actor 接口回调失败: service={}, actorInterface={}, actorClass={}",
                        id, actorInterface.getName(), byClass.getClass().getName(), e);
            }
        }
    }

    /** 派发事件；同一个事件对象会被所有监听器复用。 */
    public final <E extends Event, L extends EventListener> void publishEvent(
            Class<L> listenerType, E event, BiConsumer<L, E> consumer) {
        forEachActor(listenerType, listener -> consumer.accept(listener, event));
    }

    public final Map<Class<?>, List<Object>> getActorInterfaceMap() {
        return actorInterfaceIndexer.getInterfaceActors();
    }

    public RpcOutboundGateway getRpcOutboundGateway() {
        return rpcOutboundGateway;
    }

    public void dispatchMailboxMessageInternal(org.evd.game.runtime.call.ActorMessage message) {
        rpcInboundDispatcher.dispatchMailBoxMessage(message);
    }

    private void pulseEntity_st() {
        // todo 处理标脏的数据实体
    }

    private void pulseTask_st() {
        timerScheduler.update(getTimeCurrent());
    }

    /**
     * 刷新远程调用RPC缓冲区
     */
    private void flushCallFrameBuffers_st() {
        callTransport.flush();
    }

    /**
     * 从并发队列中转移到本线程内的队列
     * 先固定本帧开始时的数量，避免转移过程中不断吸入新任务，导致此帧执行时间不可控。
     */
    private void pulseAffirm_st() {
        int callsToAffirm = calls.getFrameProcessNum();
        int postedTasksToAffirm = postedTasks.getFrameProcessNum();
        for (int i = 0; i < callsToAffirm; i++) {
            CallBase call = calls.poll();
            if (call == null) {
                break;
            }
            affirmCalls.add(call);
        }
        for (int i = 0; i < postedTasksToAffirm; i++) {
            Runnable task = postedTasks.poll();
            if (task == null) {
                break;
            }
            affirmPostedTasks.add(task);
        }
    }

    private void pulsePostedTasks_st() {
        for (Runnable postedTask : affirmPostedTasks) {
            try {
                postedTask.run();
            } catch (Throwable e) {
                rethrowFatal(e);
                LogCore.core.error("posted service task failed: service={}", id, e);
            }
        }
        affirmPostedTasks.clear();
    }

    private void drainQueuedContinuations_st() {
        continuationRuntime.drain("frame");
    }

    /**
     * 执行call请求
     */
    private void pulseCalls_st() {
        for (CallBase call : affirmCalls) {
            rpcInboundDispatcher.handle(call);
        }
        affirmCalls.clear();
    }

    private void publishPressureSnapshot_st() {
        pressureSnapshot = new PressureSnapshot(continuationRuntime.readySize());
    }

    public void holdContinuation(Task.ContinuationWrapper conTask) {
        continuationRuntime.hold(conTask);
    }

    /**
     * 供外部线程安全投递任务到 service 线程执行
     */
    public void post(Runnable task) {
        if (task == null) {
            throw new SysException("posted task is null: service={}", id);
        }
        postedTasks.add(task);
    }

    public record PressureSnapshot(
            int readyContinuations) {
        private static final PressureSnapshot EMPTY = new PressureSnapshot(0);
    }

    /**
     * 在当前service线程里启动一个独立业务协程。
     * 适合从同步tick/普通回调里触发需要callWait/sleep的业务流程。
     */
    public final void launchCoroutine(Runnable task) {
        if (task == null) {
            throw new SysException("launch coroutine task is null: service={}", id);
        }
        if (getCurrent() != this) {
            throw new SysException("launchCoroutine must run on its service thread; use postCoroutine instead: service={}", id);
        }
        continuationRuntime.createAndEnterQueue(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                rethrowFatal(e);
                LogCore.core.error("service coroutine failed: service={}", id, e);
            }
        }, null, Task.Reason.NORMAL, null);
    }

    /**
     * 线程安全地投递一个业务协程到service线程启动。
     */
    public final void postCoroutine(Runnable task) {
        if (task == null) {
            throw new SysException("post coroutine task is null: service={}", id);
        }
        post(() -> launchCoroutine(task));
    }

    private static void rethrowFatal(Throwable throwable) {
        if (throwable instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
    }

    private void onContinuationComplete(Task.ContinuationWrapper continuation) {
        if (!coroutineLockManager.owns(continuation)) {
            return;
        }
        LogCore.core.error(
                "协程锁未显式释放，走运行时保底释放: service={}, conId={}, actorId={}, locks={}",
                id,
                continuation.getConId(),
                continuation.getActorId(),
                coroutineLockManager.buildOwnedLockDebugInfo(continuation));
        coroutineLockManager.releaseAll(continuation);
    }

    /**
     * 创建call请求，并发送到目标service
     * 针对不需要返回结果的call请求
     *
     * @param toCallPoint
     * @param methodKey
     * @param params
     */
    public void call(CallPoint toCallPoint, int methodKey, Object[] params) {
        rpcOutboundGateway.call(requireCallPoint(toCallPoint, methodKey), methodKey, params);
    }

    /**
     * 创建call请求，并发送到目标service
     * 针对需要返回结果的call请求
     *
     * @param toCallPoint
     * @param methodKey
     * @param params
     */
    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params) {
        return rpcOutboundGateway.callWait(
                requireCallPoint(toCallPoint, methodKey), methodKey, params, getCallWaitTimeoutInternal());
    }

    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        return rpcOutboundGateway.callWait(requireCallPoint(toCallPoint, methodKey), methodKey, params, timeoutMillis);
    }

    private CallPoint requireCallPoint(CallPoint callPoint, int methodKey) {
        if (callPoint == null) {
            throw new SysException("rpc target call point is null: service={}, methodKey={}", id, methodKey);
        }
        return callPoint;
    }


    public Object callWait(RpcCallBase rpcCallBase, long timeoutMillis) {
        return rpcOutboundGateway.callWait(rpcCallBase, timeoutMillis);
    }

    /**
     * 协程等待指定时间，常用于需要显式超时点的业务逻辑
     */
    public void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        Task.ContinuationWrapper thisContinuation = requireRunningContinuation();
        thisContinuation.prepareWait();
        thisContinuation.markWaiting(new ContinuationDebugInfo.SleepDebugInfo(delayMillis));
        timerScheduler.scheduleDelay(getWaitBaseTime(), delayMillis, () -> {
            continuationRuntime.resume(thisContinuation, null, Task.Reason.TIMER);
        });
        thisContinuation.waitResult();
    }

    public void sendClientCmd(CallPoint toCallPoint, org.evd.game.runtime.client.ClientSessionRef session,
                              int msgId, Chunk body) {
        rpcOutboundGateway.sendClientCmd(toCallPoint, session, msgId, body);
    }

    public void dispatchLocalClientCmd(org.evd.game.runtime.client.ClientSessionRef session,
                                       int msgId, Chunk body) {
        try {
            rpcMethodInvoker.dispatchClientCmd(msgId, new Object[]{session, body});
        } catch (Exception e) {
            throw new IllegalStateException("本地客户端协议分发失败: service=" + id + ", msgId=" + msgId, e);
        }
    }

    public Task.ContinuationWrapper requireRunningContinuation() {
        return continuationRuntime.requireRunning();
    }

    protected final Task.ContinuationWrapper currentContinuation() {
        return requireRunningContinuation();
    }

    protected final void resumeContinuation(Task.ContinuationWrapper continuation,
                                            Object result) {
        if (continuation == null) {
            return;
        }
        continuationRuntime.resume(continuation, result, Task.Reason.RPC);
    }

    protected final void failContinuation(Task.ContinuationWrapper continuation,
                                          RuntimeException failure) {
        if (continuation == null) {
            return;
        }
        continuationRuntime.fail(continuation, failure, Task.Reason.RPC);
    }

    public final <T> T awaitCompletionStage(CompletionStage<T> stage, long timeoutMillis) {
        Task.ContinuationWrapper continuation = requireRunningContinuation();
        continuation.prepareWait();
        continuation.markWaiting(new ContinuationDebugInfo.CompletionStageWaitDebugInfo(stage.getClass(), timeoutMillis));
        long timerId = timeoutMillis > 0L
                ? timerScheduler.scheduleDelay(getWaitBaseTime(), timeoutMillis, () -> {
                    continuationRuntime.fail(
                            continuation,
                            new SysException("async wait timeout: service={}, timeoutMillis={}", id, timeoutMillis),
                            Task.Reason.TIMER);
                })
                : 0L;
        try {
            stage.whenComplete((result, throwable) -> post(() -> {
                if (timerId != 0L && !timerScheduler.cancel(timerId)) {
                    return;
                }
                if (throwable != null) {
                    Throwable cause = unwrapCompletionFailure(throwable);
                    RuntimeException failure = cause instanceof RuntimeException runtimeException
                            ? runtimeException
                            : new SysException(cause);
                    continuationRuntime.fail(continuation, failure, Task.Reason.RPC);
                    return;
                }
                continuationRuntime.resume(continuation, result, Task.Reason.RPC);
            }));
        } catch (RuntimeException | Error registrationFailure) {
            if (timerId != 0L) {
                timerScheduler.cancel(timerId);
            }
            throw registrationFailure;
        }
        @SuppressWarnings("unchecked")
        T result = (T) continuation.waitResult();
        return result;
    }

    private Throwable unwrapCompletionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }


    public final ContinuationLockScope awaitCoroutineLockScope(LockType type, Object key) {
        return awaitCoroutineLockScope(type, key, CoroutineLockManager.DEFAULT_TIMEOUT_MILLIS);
    }

    public final ContinuationLockScope awaitCoroutineLockScope(LockType type, Object key, int timeoutMillis) {
        return coroutineLockManager.await(type, key, timeoutMillis);
    }

    protected final long newOnceTimer(long delayMillis, Runnable callback) {
        return timerScheduler.scheduleDelay(getWaitBaseTime(), delayMillis, callback);
    }

    protected final long newRepeatedTimer(long intervalMillis, boolean immediate, Runnable callback) {
        return timerScheduler.scheduleRepeated(getWaitBaseTime(), intervalMillis, immediate, callback);
    }

    /**
     * 创建一个到期后在独立业务协程中执行的单次定时器。
     * 适合回调中可能调用 callWait、sleep 或其他协程等待 API 的业务逻辑。
     */
    protected final long newOnceTimerCoroutine(long delayMillis, Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        return newOnceTimer(delayMillis, () -> launchCoroutine(callback));
    }

    /**
     * 创建一个每次到期后都在独立业务协程中执行的重复定时器。
     * 每次触发都会创建新的协程，不会等待上一次回调完成。
     */
    protected final long newRepeatedTimerCoroutine(long intervalMillis,
                                                    boolean immediate,
                                                    Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        return newRepeatedTimer(intervalMillis, immediate, () -> launchCoroutine(callback));
    }

    protected final boolean removeTimer(long timerId) {
        return timerScheduler.cancel(timerId);
    }

    public final TimerScheduler timerScheduler() {
        return timerScheduler;
    }

    /**
     * 调试用：打印当前 service 内部所有协程状态、等待点和锁状态。
     */
    public final String buildCoroutineDebugDump(String reason) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("协程调试快照: service=").append(id)
                .append(", class=").append(getClass().getSimpleName())
                .append(", status=").append(status)
                .append(", schedule=").append(scheduledName);
        if (reason != null && !reason.isBlank()) {
            sb.append(", reason=").append(reason);
        }
        sb.append('\n');
        sb.append(continuationRuntime.buildDebugDump());
        sb.append(coroutineLockManager.buildDebugDump());
        return sb.toString();
    }

    public final String buildCoroutineDebugDump() {
        return buildCoroutineDebugDump("manual");
    }

    public final void logCoroutineDebugDump(String reason) {
        LogCore.core.error(buildCoroutineDebugDump(reason));
    }

    public final void logCoroutineDebugDump() {
        logCoroutineDebugDump("manual");
    }

    protected void registerActor(ActorId actorId, MailBoxType executionMode) {
        actorMailBoxRegistry.register(actorId, executionMode);
    }

    protected void unregisterActor(ActorId actorId) {
        actorMailBoxRegistry.unregister(actorId);
    }

    protected boolean hasActor(ActorId actorId) {
        return actorMailBoxRegistry.contains(actorId);
    }

    protected ActorAddress getActorAddress(ActorId actorId) {
        MailBoxBean mailBoxBean = actorMailBoxRegistry.requireMailBox(actorId);
        return new ActorAddress(getCallPoint(), mailBoxBean.getEpoch());
    }

    public MessageSender getMessageSender() {
        return messageSender;
    }

    public MessageLocationSender getMessageLocationSender() {
        return messageLocationSender;
    }

    /**
     * 关服逻辑要写这里，等这个方法结束就结束，协程运行
     */
    @Override
    protected final void onStopInternal(boolean force) {
            super.onStopInternal(force);
        boolean success = false;
        try {
            onStop(force);
            success = true;
        } finally {
            // 数据库在force的情况下 强制再同步一次
            if (success || force) {
                if (mdb != null && !mdb.isClosing()) {
                    mdb.close();
                    mdb = null;
                }
            }
        }
    }

    protected void onStop(boolean force) {

    }



    @Override
    public void onClose() {
        try {
            super.onClose();
            callTransport.close();
            coroutineLockManager.close();
            continuationRuntime.close();
            timerScheduler.close();

            if (messageLocationSender != null) {
                messageLocationSender.close();
            }
        } finally {
            // 即使本地资源关闭失败，也必须让 Node 脱离该 Service，避免整机停服永久等待。
            // rpcStop 回包已经更早投递到同一 Node 队列，FIFO 保证先发回包、后移除 Service。
            node.remove(this);
        }
    }

    /**
     * 获取系统时间
     *
     * @return
     */
    public static long getTime() {
        return getCurrent().getTimeCurrent();
    }

    public Mdb getMdb() {
        return mdb;
    }

    /**
     * 根据 dbDef APT 生成的表注册器自动判断当前 Service 是否需要 MDB。
     */
    protected final boolean supportMdb() {
        return Mdb.hasTableRegistry(getClass());
    }

    /** 是否在本 Service 初始化阶段触发公共配置表加载。 */
    protected boolean supportTable() {
        return true;
    }

    protected boolean supportLocation() {
        return true;
    }

    /** 发现新的 Service，可能包含自己；此时 Service 还没有进入正式路由索引。 */
    protected void onServiceConnect(Collection<RegisteredService> serviceList) {
    }

    /** 新 Service 已结束稳定等待，并已经进入正式路由索引。 */
    protected void onServiceConnectReady(Collection<RegisteredService> serviceList) {
        if (mdb != null) {
            mdb.connectService(serviceList);
        }
    }

    /** Service 从最新注册快照消失，可能包含自己。 */
    protected void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        if (mdb != null) {
            mdb.disconnectService(serviceList);
        }
    }

    /** Service 已离线超过保留时间并从 offlineServices 中彻底清理。 */
    protected void onServiceOfflineExpired(Collection<RegisteredService> serviceList) {
    }

    @Override
    public String toString() {
        return "Service{" +
                "serviceInfo=" + serviceInfo +
                ", id='" + id + '\'' +
                ", status=" + status +
                '}';
    }
}
