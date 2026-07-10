package org.evd.game.runtime;

import jdk.internal.vm.ContinuationScope;
import org.apache.logging.log4j.ThreadContext;
import org.evd.game.annotation.ServiceName;
import org.evd.game.annotation.ServiceType;
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
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.config.RegisteredService;
import org.evd.game.runtime.config.ServiceInfo;
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
import org.evd.game.runtime.support.exception.SysException;
import org.evd.game.runtime.util.TimerScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 服务
 */
public class Service extends TickCase {
    public void addCall_snt(CallBase call) {
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
    private final ConcurrentLinkedDeque<CallBase> calls = new ConcurrentLinkedDeque<>();
    /**
     * 此帧要执行的calls
     */
    private final List<CallBase> affirmCalls = new ArrayList<>();
    /**
     * 非 service 线程投递过来的任务
     */
    private final ConcurrentLinkedDeque<Runnable> postedTasks = new ConcurrentLinkedDeque<>();
    /**
     * 此帧要执行的投递任务
     */
    private final List<Runnable> affirmPostedTasks = new ArrayList<>();
    /**
     * 协程的组，与service同名
     */
    private final ContinuationScope scope;

    public ContinuationScope getScope() {
        return scope;
    }

    /**
     * 通用协程锁
     */
    private final CoroutineLockManager coroutineLockManager = new CoroutineLockManager(this);

    /**
     * 当前 service 内的 actor mailbox 注册表
     */
    private final ActorMailBoxRegistry actorMailBoxRegistry = new ActorMailBoxRegistry(this);
    /**
     * 通用定时调度器
     */
    private final TimerScheduler timerScheduler = new TimerScheduler();
    /**
     * continuation 调度与 wait/timeout
     */
    private final ContinuationRuntime continuationRuntime = new ContinuationRuntime(this, timerScheduler);
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
    /** init continuation 完整结束后才允许发布服务和执行正常业务 tick。 */
    private volatile boolean initialized;
    private volatile Throwable initializationFailure;
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
        this.callPoint = new CallPoint(node.getId(), name);
        this.callTransport = new CallTransport(node, name);
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
            // 修改状态
            status = CaseStatus.Running;
            // 先执行初始化
            initVirtual_t();
        } finally {
            threadLocal.remove();
        }
    }

    /**
     * init由协程执行，交给子类继承
     */
    @Override
    public final void _init() {
        super._init();
        // 初始化期间先允许本节点投递响应，但未 READY 前不会出现在服务注册快照中。
        node.attachToNode(this);
        if (messageLocationSender != null) {
            newRepeatedTimer(
                    MessageLocationSender.getCleanupIntervalMillis(),
                    false,
                    messageLocationSender::cleanupIdle);
        }

        if (supportMdb()) {
            this.mdb = new Mdb();
            // DBService 的 READY 顺序独立，MDB 仍在服务 tick 中异步启动，避免初始化依赖环。
            postCoroutine(() -> mdb.start(getClass(), (DBExecInterface) ServiceName.getRpcProxyObj(ServiceName.DB_SERVICE), this));
        }

        init();

        // init 可能挂起；只有完整成功返回后，服务才对本地和远端路由可见。
        initialized = true;
        node.publishService(this);
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
                initializationFailure = e;
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
            if (initializationFailure != null) {
                return;
            }
            pulseAffirm_st();
            drainQueuedContinuations_st("afterAffirm");

            pulsePostedTasks_st();
            // 初始化期 Node 只允许 CallResult 入队，用于恢复 init continuation。
            pulseCalls_st();
            drainQueuedContinuations_st("afterCalls");

            if (initialized) {
                tick_st();
            }

            pulseTask_st();
            drainQueuedContinuations_st("afterTimers");
            if (initialized) {
                pulseEntity_st();
            }

            //刷新call发送缓冲区
            flushCallFrameBuffers_st();
        } finally {
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

    public CoroutineLockManager coroutineLockManagerInternal() {
        return coroutineLockManager;
    }

    public boolean sendOutboundCall(CallBase call) {
        return callTransport.send(call);
    }

    public CallPoint copyCallPoint() {
        return new CallPoint(callPoint);
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
        if (actorType == null) {
            throw new SysException("actorType is null: service={}", id);
        }
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
     * 如果取一个执行一个，可能因为执行时间长 同时并发队列一直被add，导致源源不断从并发队列中取出call，从而导致此帧时间过长
     */
    private void pulseAffirm_st() {
        while (!calls.isEmpty()) {
            affirmCalls.add(calls.poll());
        }
        while (!postedTasks.isEmpty()) {
            affirmPostedTasks.add(postedTasks.poll());
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

    private void drainQueuedContinuations_st(String phase) {
        continuationRuntime.drain(phase);
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

    public void unHoldContinuation(Task.ContinuationWrapper conTask) {
        continuationRuntime.unhold(conTask, () -> {
            if (!coroutineLockManager.owns(conTask)) {
                return;
            }
            LogCore.core.error("协程锁未显式释放，走unHoldContinuation保底释放: service={}, conId={}, actorId={}",
                    id, conTask.getConId(), conTask.getActorId());
            coroutineLockManager.release(conTask);
        });
    }

    public boolean isInitialized() {
        return initialized;
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
        rpcOutboundGateway.call(toCallPoint, methodKey, params);
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
        return rpcOutboundGateway.callWait(toCallPoint, methodKey, params);
    }

    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        return rpcOutboundGateway.callWait(toCallPoint, methodKey, params, timeoutMillis);
    }

    /**
     * 协程等待指定时间，常用于需要显式超时点的业务逻辑
     */
    public void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        Task.ContinuationWrapper thisContinuation = requireRunningContinuation();
        registerWait(
                delayMillis,
                (continuation, waitId) -> continuation.setResult(null),
                new ContinuationDebugInfo.SleepDebugInfo(delayMillis));
        thisContinuation.waitResult();
    }

    public void sendClientCmd(CallPoint toCallPoint, ClientSessionRef session, int msgId, Chunk body) {
        rpcOutboundGateway.sendClientCmd(toCallPoint, session, msgId, body);
    }

    public Task.ContinuationWrapper requireRunningContinuation() {
        return continuationRuntime.requireRunning();
    }

    long registerWait(long timeoutMillis, ContinuationRuntime.WaitTimeoutHandler timeoutHandler) {
        return continuationRuntime.registerWait(timeoutMillis, getWaitBaseTime(), timeoutHandler);
    }

    public long registerWait(long timeoutMillis,
                      ContinuationRuntime.WaitTimeoutHandler timeoutHandler,
                      ContinuationDebugInfo.DebugInfo waitDebugInfo) {
        return continuationRuntime.registerWait(timeoutMillis, getWaitBaseTime(), timeoutHandler, waitDebugInfo);
    }

    public Task.ContinuationWrapper _takeWaitContinuation(long waitId) {
        return continuationRuntime.takeWaitContinuation(waitId);
    }

    public void _queueUnlockContinuation(Task.ContinuationWrapper continuation) {
        continuationRuntime.queue(continuation, Task.Reason.UNLOCK);
    }

    protected final Task.ContinuationWrapper currentContinuation() {
        return requireRunningContinuation();
    }

    protected final void resumeContinuation(Task.ContinuationWrapper continuation, Object result) {
        if (continuation == null) {
            return;
        }
        continuation.setResult(result);
        continuationRuntime.queue(continuation, Task.Reason.RPC);
    }

    protected final void failContinuation(Task.ContinuationWrapper continuation, RuntimeException failure) {
        if (continuation == null) {
            return;
        }
        continuation.setFailure(failure);
        continuationRuntime.queue(continuation, Task.Reason.RPC);
    }

    protected final <T> T awaitCompletionStage(CompletionStage<T> stage, long timeoutMillis) {
        Task.ContinuationWrapper continuation = requireRunningContinuation();
        long waitId = registerWait(
                timeoutMillis,
                (ctx, timeoutWaitId) -> ctx.setFailure(new SysException("async wait timeout: service={}, waitId={}, timeoutMillis={}",
                        id, timeoutWaitId, timeoutMillis)),
                new ContinuationDebugInfo.CompletionStageWaitDebugInfo(stage.getClass(), timeoutMillis));
        stage.whenComplete((result, throwable) -> post(() -> {
            Task.ContinuationWrapper waitContinuation = _takeWaitContinuation(waitId);
            if (waitContinuation == null) {
                return;
            }
            if (throwable != null) {
                Throwable cause = unwrapCompletionFailure(throwable);
                RuntimeException failure = cause instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new SysException(cause);
                failContinuation(waitContinuation, failure);
                return;
            }
            resumeContinuation(waitContinuation, result);
        }));
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
        if (key == null) {
            return new ContinuationLockScope(coroutineLockManager, null);
        }
        coroutineLockManager.await(type, key, timeoutMillis);
        return new ContinuationLockScope(coroutineLockManager, currentContinuation());
    }

    protected final long newOnceTimer(long delayMillis, Runnable callback) {
        return timerScheduler.scheduleDelay(getWaitBaseTime(), delayMillis, callback);
    }

    protected final long newRepeatedTimer(long intervalMillis, boolean immediate, Runnable callback) {
        return timerScheduler.scheduleRepeated(getWaitBaseTime(), intervalMillis, immediate, callback);
    }

    protected final boolean removeTimer(long timerId) {
        return timerScheduler.cancel(timerId);
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
        return new ActorAddress(copyCallPoint(), mailBoxBean.getEpoch());
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
    protected void onStop() {
        super.onStop();

        if (mdb != null && !mdb.isClosing()) {
            mdb.close();
            mdb = null;
        }

    }

    @Override
    public void onClose() {
        super.onClose();
        node.remove(this);

        if (messageLocationSender != null) {
            messageLocationSender.close();
        }
        callTransport.close();
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

    protected boolean supportMdb() {
        return false;
    }

    protected boolean supportLocation() {
        return true;
    }

    /**
     * 有新的service连接进来;可能包含自己
     */
    protected void onServiceConnect(Collection<RegisteredService> serviceList) {
        if (mdb != null) {
            mdb.connectService(serviceList);
        }
    }

    /**
     * 有新的service断链;可能包含自己
     */
    protected void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        if (mdb != null) {
            mdb.disconnectService(serviceList);
        }
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
