package org.evd.game.runtime;

import jdk.internal.vm.ContinuationScope;
import org.evd.game.annotation.ServiceName;
import org.evd.game.runtime.Db.table.Mdb;
import org.evd.game.runtime.actor.ActorAddress;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorMailBoxRegistry;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.config.RegisteredService;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.continuation.ContinuationLockScope;
import org.evd.game.runtime.continuation.ContinuationRuntime;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.mailbox.MailBoxBean;
import org.evd.game.runtime.mailbox.MessageLocationSender;
import org.evd.game.runtime.mailbox.ProcessInnerSender;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.SysException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 服务
 */
public class Service extends TickCase {
    /**
     * 通用协程锁类型: actor
     */
    protected static final int COROUTINE_LOCK_TYPE_ACTOR = 1;
    /**
     * mailbox 线性化锁类型
     */
    public static final int COROUTINE_LOCK_TYPE_MAILBOX = 2;


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

    protected ServiceInfo serviceInfo;
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


        if (supportLocation()) this.messageLocationSender = new MessageLocationSender(this);
        if (supportMdb()) {
            this.mdb = new Mdb();
            try {
                mdb.start(getClass(), (DBExecInterface) ServiceName.getRpcProxyObj(ServiceName.DB_SERVICE));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    @Override
    protected void init_t() {
        // 加入到services
        node.attachToNode(this);
        if(messageLocationSender != null) {
            newRepeatedTimer(
                    MessageLocationSender.getCleanupIntervalMillis(),
                    false,
                    messageLocationSender::cleanupIdle);
        }

        // 修改状态
        status = CaseStatus.Running;
        // 先执行初始化
        initVirtual_t();
    }

    /**
     * init方法交给协程执行
     * 因为init中可能存在异步操作，异步可能触发协程yield，导致线程yield
     */
    private void initVirtual_t() {
        continuationRuntime.createAndRun(new Task.TaskParam0(this::init), null);
    }

    @Override
    protected void pulse() {
        // service放到threadLocal，以便于逻辑中从当前上线文中获取
        threadLocal.set(this);

        pulseAffirm_st();
        drainQueuedContinuations_st("afterAffirm");

        pulsePostedTasks_st();
        pulseCalls_st();
        drainQueuedContinuations_st("afterCalls");

        tick_st();

        pulseTask_st();
        drainQueuedContinuations_st("afterTimers");
        pulseEntity_st();

        //刷新call发送缓冲区
        flushCallFrameBuffers_st();

        // 逻辑结束后移除，因为下次tick会分配其他线程
        threadLocal.remove();
    }

    /**
     * tick保持同步驱动。
     * 需要等待RPC/定时器的逻辑，应该显式启动独立业务协程，而不是阻塞tick本身。
     */
    private void tick_st() {
        tick();
    }

    public void tick() {
        if (mdb != null) {
            mdb.tick(getTime());
        }
    }

    /**
     * 返回rpc同步等待的默认超时时间，单位毫秒
     * 小于等于0代表不启用超时
     */
    protected long getCallWaitTimeout() {
        return -1L;
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

    long getWaitBaseTimeInternal() {
        return getWaitBaseTime();
    }

    ProcessInnerSender getProcessInnerSender() {
        return processInnerSender;
    }

    RpcMethodInvoker getRpcMethodInvoker() {
        return rpcMethodInvoker;
    }

    RpcOutboundGateway getRpcOutboundGateway() {
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
        continuationRuntime.createAndEnterQueue(() -> {
            try {
                task.run();
            } catch (Throwable e) {
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
        registerWait(delayMillis, (continuation, waitId) -> continuation.setResult(null));
        thisContinuation.waitResult();
    }

    public void sendClientCmd(CallPoint toCallPoint, ClientSessionRef session, int msgId, Chunk body) {
        rpcOutboundGateway.sendClientCmd(toCallPoint, session, msgId, body);
    }

    Task.ContinuationWrapper requireRunningContinuation() {
        return continuationRuntime.requireRunning();
    }

    long registerWait(long timeoutMillis, ContinuationRuntime.WaitTimeoutHandler timeoutHandler) {
        return continuationRuntime.registerWait(timeoutMillis, getWaitBaseTime(), timeoutHandler);
    }

    Task.ContinuationWrapper takeWaitContinuation(long waitId) {
        return continuationRuntime.takeWaitContinuation(waitId);
    }

    void queueUnlockContinuation(Task.ContinuationWrapper continuation) {
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

    public final void awaitCoroutineLock(int type, Object key) {
        coroutineLockManager.await(type, key);
    }

    public final void awaitCoroutineLock(int type, Object key, int timeoutMillis) {
        coroutineLockManager.await(type, key, timeoutMillis);
    }

    protected final ContinuationLockScope awaitCoroutineLockScope(int type, Object key) {
        return awaitCoroutineLockScope(type, key, CoroutineLockManager.DEFAULT_TIMEOUT_MILLIS);
    }

    protected final ContinuationLockScope awaitCoroutineLockScope(int type, Object key, int timeoutMillis) {
        if (key == null) {
            return new ContinuationLockScope(coroutineLockManager,null);
        }
        awaitCoroutineLock(type, key, timeoutMillis);
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

    @Override
    public void onClose() {
        messageLocationSender.close();

        node.remove(this);

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

    }

    /**
     * 有新的service断链;可能包含自己
     */
    protected void onServiceDisconnect(Collection<RegisteredService> serviceList) {
        if(mdb != null) {
            mdb.disconnectService(serviceList);
        }
    }
}
