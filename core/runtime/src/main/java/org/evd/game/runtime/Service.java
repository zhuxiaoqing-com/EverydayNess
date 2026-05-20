package org.evd.game.runtime;

import jdk.internal.vm.ContinuationScope;
import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallResult;
import org.evd.game.runtime.actor.ActorExecutionMode;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.ActorRegistry;
import org.evd.game.runtime.serialize.CallPulseBuffer;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;
import org.evd.game.runtime.support.SysException;
import org.evd.game.runtime.support.function.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 服务
 */
public class Service extends TickCase{
    /** 通用协程锁类型: actor */
    protected static final int COROUTINE_LOCK_TYPE_ACTOR = 1;

    public void addCall_snt(CallBase call) {
        calls.add(call);
    }

    enum ServiceStatus{
        New,
        Running,
        PendingKill,
        Closed
    }
    /** node */
    protected final Node node;    public Node getNode() { return node; }
    /** 线程池名字 */
    private final String scheduledName;     public String getScheduledName(){ return scheduledName; }

    /** service的接收队列 */
    private final ConcurrentLinkedDeque<CallBase> calls = new ConcurrentLinkedDeque<>();
    /** 此帧要执行的calls */
    private final List<CallBase> affirmCalls = new ArrayList<>();
    /** 协程的组，与service同名 */
    private final ContinuationScope scope; public ContinuationScope getScope() { return scope; }
    /** 通用协程锁 */
    private final CoroutineLockManager coroutineLockManager = new CoroutineLockManager();
    /** 当前 service 内的 actor 注册表 */
    private final ActorRegistry actorRegistry = new ActorRegistry();
    /** 通用定时调度器 */
    private final TimerScheduler timerScheduler = new TimerScheduler();
    /** continuation 调度与 wait/timeout */
    private final ContinuationRuntime continuationRuntime = new ContinuationRuntime(this, timerScheduler);
    /** ThreadLocal */
    private final static ThreadLocal<Service> threadLocal = new ThreadLocal<>();
    public static Service getCurrent(){
        return threadLocal.get();
    }
    /** rpc调用路由到接收函数的类 */
    private RPCImplBase methodFunctionProxy;
    /** 本service的调用点 */
    private final CallPoint callPoint;
    /** 远程请求RPC缓冲区 */
    private final Map<String, CallPulseBuffer> callFrameBuffers = new HashMap<>();

    public Service(Node node, String name, String scheduledName, long tickInterval){
        super(name, tickInterval);
        this.node = node;
        this.scheduledName = scheduledName;
        // scope与service同名
        scope = new ContinuationScope(name);
        callPoint = new CallPoint(node.getId(), name);

    }

    public Service(Node node, String name, String scheduledName){
        this(node, name, scheduledName, TICK_INTERVAL);
    }

    @Override
    protected void init_t() {
        // 加入到services
        node.attachToNode(this);

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
        Task.ContinuationWrapper continuation = continuationRuntime.create(new Task.TaskParam0(this::init), null);
        continuationRuntime.runImmediate(continuation);
    }

    @Override
    protected void pulse() {
        // service放到threadLocal，以便于逻辑中从当前上线文中获取
        threadLocal.set(this);

        pulseAffirm_st();
        pulseCalls_st();
        pulseWaitTimeout_st();

        tickVirtual_st();

        pulseTask_st();
        pulseEntity_st();

        //刷新call发送缓冲区
        flushCallFrameBuffers_st();

        // 逻辑结束后移除，因为下次tick会分配其他线程
        threadLocal.remove();
    }

    /**
     * tick交给协程执行
     */
    private void tickVirtual_st() {
        Task.ContinuationWrapper context = continuationRuntime.create(new Task.TaskParam0(this::tick), null);
        continuationRuntime.runImmediate(context);
    }

    public void tick() {

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

    private void pulseEntity_st() {
        // todo 处理标脏的数据实体
    }

    private void pulseTask_st() {
        // todo 定时任务
    }

    /**
     * 检查等待中的协程是否超时
     */
    private void pulseWaitTimeout_st() {
        timerScheduler.update(getTimeCurrent());
    }

    /**
     * 刷新远程调用RPC缓冲区
     */
    private void flushCallFrameBuffers_st() {
        for (CallPulseBuffer frameCache : callFrameBuffers.values()) {
            try {
                frameCache.flush_st(node);
            } catch (Throwable e) {
                // 不做任何处理 仅仅抛出异常
                // 避免因为一个任务的出错 造成后续的任务无法继续执行 需要等到下一个心跳
                LogCore.core.error("", e);
                /*log.error("", e);*/
            }
        }
    }

    /**
     * 从并发队列中转移到本线程内的队列
     * 如果取一个执行一个，可能因为执行时间长 同时并发队列一直被add，导致源源不断从并发队列中取出call，从而导致此帧时间过长
     */
    private void pulseAffirm_st() {
        while (!calls.isEmpty()){
            affirmCalls.add(calls.poll());
        }
    }

    /**
     * 执行call请求
     */
    private void pulseCalls_st() {
        for (CallBase call : affirmCalls){
            dispatchCall_st(call);
        }
        affirmCalls.clear();
        continuationRuntime.drain();
    }

    private void dispatchCall_st(CallBase callbase) {
        Task.ContinuationWrapper context;
        // 发送的call
        if (callbase instanceof Call call){
            context = createCallContinuation(call, call.getActorId());
        }
        // 返回的callResult
        else {
            CallResult callResult = (CallResult)callbase;
            context = takeWaitContinuation(callResult.id);
            // 已经超时
            if (context == null){
                LogCore.core.warn("callback is null or timeout, waitId={}", callResult.id);
                return;
            }
            if (callResult.success) {
                context.setResult(callResult.result);
            } else {
                context.setFailure(new RpcCallException(
                        callResult.errorCode,
                        "rpc call failed: service=" + id + ", waitId=" + callResult.id + ", errorCode="
                                + callResult.errorCode + ", message=" + callResult.errorMessage));
            }
        }
        continuationRuntime.queue(context);
    }

    /**
     * 派发到对应的rpc监听函数
     * @param call
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch_st(Call call){
        // todo 这里没有actorId 是不是应该报错呢？
        if (call.actorId != null) {
            dispatchActorCallWithResult_st(call);
            return;
        }

        dispatchBusinessCall_st(call);
    }

    private void dispatchActorCallWithResult_st(Call call) {
        if (!call.needResult) {
            dispatchActorCall_st(call);
            return;
        }

        CallResult callReturn = call.createReturn();
        try {
            dispatchActorCall_st(call);
        } catch (Throwable e) {
            LogCore.core.error("actor rpc dispatch failed: service={}, actorId={}, methodKey={}", id, call.actorId, call.methodKey, e);
            fillRpcFailure(callReturn, e);
            sendCall_st(callReturn);
        }
    }

    private void dispatchActorCall_st(Call call) {
        ActorRegistry.Registration registration = actorRegistry.requireRegistration(call.actorId);
        Call businessCall = ActorForwarding.unwrapOrOriginal(id, call);
        switch (registration.getExecutionMode()) {
            case ORDERED -> dispatchOrderedActorCall_st(businessCall, registration.getRegistrationId());
            case UNORDERED -> dispatchUnorderedActorCall_st(businessCall);
        }
    }

    final void dispatchOrderedActorCall_st(Call call, long registrationId) {
        Task.ContinuationWrapper continuation = requireRunningContinuation();
        awaitCoroutineLock(COROUTINE_LOCK_TYPE_ACTOR, new ActorId(call.actorId));
        try {
            actorRegistry.requireSameRegistration(call.actorId, registrationId);
            dispatchBusinessCall_st(call);
        } finally {
            releaseCoroutineLock(continuation);
        }
    }

    final void dispatchUnorderedActorCall_st(Call call) {
        dispatchBusinessCall_st(call);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatchBusinessCall_st(Call call) {
        Object func = getMethodFunction(call.methodKey);
        Object[] m = call.methodParam;
        if (call.needResult){
            CallResult callReturn = call.createReturn();
            try {
                callReturn.result = invokeRpc(func, m);
            } catch (Throwable e) {
                LogCore.core.error("rpc return dispatch failed: service={}, methodKey={}", id, call.methodKey, e);
                fillRpcFailure(callReturn, e);
            }
            sendCall_st(callReturn);
        }else{
            try {
                invokeRpc(func, m);
            }catch (Exception e){
                LogCore.core.error("rpc dispatch failed: service={}, methodKey={}", id, call.methodKey, e);
            }
        }
    }

    private Object invokeRpc(Object func, Object[] args) throws InterruptedException {
        switch (args.length) {
            case 0:
                if (func instanceof ReturnFunction0 returnFunc) {
                    return returnFunc.apply();
                }
                ((Function0) func).apply();
                return null;
            case 1:
                if (func instanceof ReturnFunction1 returnFunc) {
                    return returnFunc.apply(args[0]);
                }
                ((Function1) func).apply(args[0]);
                return null;
            case 2:
                if (func instanceof ReturnFunction2 returnFunc) {
                    return returnFunc.apply(args[0], args[1]);
                }
                ((Function2) func).apply(args[0], args[1]);
                return null;
            case 3:
                if (func instanceof ReturnFunction3 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2]);
                }
                ((Function3) func).apply(args[0], args[1], args[2]);
                return null;
            case 4:
                if (func instanceof ReturnFunction4 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3]);
                }
                ((Function4) func).apply(args[0], args[1], args[2], args[3]);
                return null;
            case 5:
                if (func instanceof ReturnFunction5 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4]);
                }
                ((Function5) func).apply(args[0], args[1], args[2], args[3], args[4]);
                return null;
            case 6:
                if (func instanceof ReturnFunction6 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5]);
                }
                ((Function6) func).apply(args[0], args[1], args[2], args[3], args[4], args[5]);
                return null;
            case 7:
                if (func instanceof ReturnFunction7 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
                }
                ((Function7) func).apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
                return null;
            case 8:
                if (func instanceof ReturnFunction8 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
                }
                ((Function8) func).apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
                return null;
            case 9:
                if (func instanceof ReturnFunction9 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
                }
                ((Function9) func).apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
                return null;
            case 10:
                if (func instanceof ReturnFunction10 returnFunc) {
                    return returnFunc.apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9]);
                }
                ((Function10) func).apply(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9]);
                return null;
            default:
                return null;
        }
    }

    public void holdContinuation(Task.ContinuationWrapper conTask){
        continuationRuntime.hold(conTask);
    }
    public void unHoldContinuation(Task.ContinuationWrapper conTask){
        continuationRuntime.unhold(conTask, () -> {});
    }

    /**
     * 创建call请求，并发送到目标service
     * 针对不需要返回结果的call请求
     * @param toCallPoint
     * @param methodKey
     * @param params
     */
    public void call(CallPoint toCallPoint, int methodKey, Object[] params) {
        call(toCallPoint, null, methodKey, params);
    }

    public void call(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params) {
        Call call = new Call();
        call.from = this.callPoint;
        call.to = toCallPoint;
        call.actorId = actorId == null ? null : new ActorId(actorId);

        call.methodKey = methodKey;
        call.methodParam = params;

        sendCall_st(call);
    }

    public void locationSend(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params) {
        Call call = ActorForwarding.createMessageEnvelope(this.callPoint, toCallPoint, actorId, methodKey, params);
        if (!sendCall_st(call)) {
            throw new SysException("send location message failed: service={}, toNode={}, toService={}, methodKey={}",
                    id, toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }
    }

    /**
     * 创建call请求，并发送到目标service
     * 针对需要返回结果的call请求
     * @param toCallPoint
     * @param methodKey
     * @param params
     */
    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params) {
        return callWait(toCallPoint, null, methodKey, params, getCallWaitTimeout());
    }

    public Object callWait(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params) {
        return callWait(toCallPoint, actorId, methodKey, params, getCallWaitTimeout());
    }

    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        return callWait(toCallPoint, null, methodKey, params, timeoutMillis);
    }

    public Object callWait(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params, long timeoutMillis) {
        Task.ContinuationWrapper thisContinuation = requireRunningContinuation();
        long waitId = registerWait(timeoutMillis,
                (continuation, timeoutWaitId) -> continuation.setFailure(
                        new SysException("rpc call timeout: service={}, waitId={}", id, timeoutWaitId)));

        Call call = buildCall(toCallPoint, actorId, methodKey, params);
        call.id = waitId;
        call.needResult = true;

        if (!sendCall_st(call)) {
            continuationRuntime.takeWaitContinuation(waitId);
            throw new SysException("send rpc call failed: service={}, toNode={}, toService={}, methodKey={}",
                    id, toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }

        return thisContinuation.waitResult();
    }

    public Object locationCallWait(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params) {
        return locationCallWait(toCallPoint, actorId, methodKey, params, getCallWaitTimeout());
    }

    public Object locationCallWait(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params, long timeoutMillis) {
        Task.ContinuationWrapper thisContinuation = requireRunningContinuation();
        long waitId = registerWait(timeoutMillis,
                (continuation, timeoutWaitId) -> continuation.setFailure(
                        new SysException("location rpc call timeout: service={}, waitId={}", id, timeoutWaitId)));

        Call call = ActorForwarding.createRequestEnvelope(this.callPoint, toCallPoint, actorId, methodKey, params);
        call.id = waitId;

        if (!sendCall_st(call)) {
            continuationRuntime.takeWaitContinuation(waitId);
            throw new SysException("send location rpc call failed: service={}, toNode={}, toService={}, methodKey={}",
                    id, toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }

        return thisContinuation.waitResult();
    }

    private Call buildCall(CallPoint toCallPoint, ActorId actorId, int methodKey, Object[] params) {
        Call call = new Call();
        call.from = this.callPoint;
        call.to = toCallPoint;
        call.actorId = actorId == null ? null : new ActorId(actorId);
        call.methodKey = methodKey;
        call.methodParam = params;
        return call;
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

    /**
     * 发送call请求
     * @param call
     */
    private boolean sendCall_st(CallBase call) {
        String toNodeId = call.to.nodeId;
        if (node.getId().equals(toNodeId)) {
            node.callHandle_snt(call);
            return true;
        }
        CallPulseBuffer buffer = callFrameBuffers.get(toNodeId);

        // 如果之前没有缓冲 那么就初始化一个
        if (buffer == null) {
            buffer = new CallPulseBuffer(toNodeId);
            callFrameBuffers.put(toNodeId, buffer);
        }

        // 将要发送内容放入发送缓冲中
        // 先尝试写入 如果失败(一般都是缓冲剩余空间不足)则先清空缓冲 后再尝试写入
        // 如果还是失败 那证明有可能是发送内容过大 不进行缓冲 直接发送
        if (!buffer.writeCall(call)) {
            //日志 第一次尝试写入缓冲失败
            LogCore.core.warn("第一次尝试写入缓冲失败：bufferLen={}, nodeId={}, portId={}, remoteNodeId={}", buffer.getLength(), getId(), node.getId(), toNodeId);

            //刷新缓冲区
            buffer.flush_st(node);
            //再次尝试写入缓冲
            if (!buffer.writeCall(call)) {
                //日志 第二次尝试写入缓冲失败
                LogCore.core.error("第二次尝试写入缓冲失败, call请求最大支持2M：bufferLen={}", buffer.getLength());
                return false;
            }
        }
        return true;
    }

    private Task.ContinuationWrapper requireRunningContinuation() {
        return continuationRuntime.requireRunning();
    }

    private long registerWait(long timeoutMillis, ContinuationRuntime.WaitTimeoutHandler timeoutHandler) {
        return continuationRuntime.registerWait(timeoutMillis, getWaitBaseTime(), timeoutHandler);
    }

    private Task.ContinuationWrapper takeWaitContinuation(long waitId) {
        return continuationRuntime.takeWaitContinuation(waitId);
    }

    private Task.ContinuationWrapper createCallContinuation(Call call, ActorId actorId) {
        return continuationRuntime.create(new Task.TaskParam1<>(this::dispatch_st, call), actorId);
    }

    protected final void awaitCoroutineLock(int type, Object key) {
        Task.ContinuationWrapper continuation = requireRunningContinuation();
        if (coroutineLockManager.tryAcquire(type, key, continuation)) {
            return;
        }
        continuation.prepareWait();
        continuation.waitResult();
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

    private void releaseCoroutineLock(Task.ContinuationWrapper continuation) {
        Task.ContinuationWrapper next = coroutineLockManager.release(continuation);
        if (next == null) {
            return;
        }
        next.setResult(null);
        continuationRuntime.queue(next);
    }

    protected void registerActor(ActorId actorId, Object actor, ActorExecutionMode executionMode) {
        actorRegistry.register(actorId, actor, executionMode);
    }

    protected void unregisterActor(ActorId actorId) {
        actorRegistry.unregister(actorId);
    }

    protected boolean hasActor(ActorId actorId) {
        return actorRegistry.contains(actorId);
    }

    protected <T> T requireActor(ActorId actorId, Class<T> type) {
        return actorRegistry.require(actorId, type);
    }

    public ActorId requireCurrentActorId() {
        Task.ContinuationWrapper continuation = requireRunningContinuation();
        if (continuation == null) {
            throw new SysException("current actor must run inside continuation: service={}", id);
        }

        ActorId actorId = continuation.getActorId();
        if (actorId == null) {
            throw new RpcCallException(
                    RpcErrorCodes.ACTOR_CONTEXT_MISSING,
                    "rpc actor context missing: service=" + id);
        }
        return new ActorId(actorId);
    }

    public <T> T requireCurrentActor(Class<T> type) {
        return requireActor(requireCurrentActorId(), type);
    }

    private void fillRpcFailure(CallResult callReturn, Throwable e) {
        callReturn.success = false;
        if (e instanceof RpcCallException rpcCallException) {
            callReturn.errorCode = rpcCallException.getErrorCode();
            callReturn.errorMessage = rpcCallException.getMessage();
            return;
        }
        if (e instanceof SysException sysException) {
            callReturn.errorCode = RpcErrorCodes.UNKNOWN;
            callReturn.errorMessage = sysException.getMessage();
            return;
        }
        callReturn.errorCode = RpcErrorCodes.UNKNOWN;
        callReturn.errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * 通过methodKey获得函数指针
     * @param methodKey
     * @return
     */
    private Object getMethodFunction(int methodKey) {
        try {
            // 获取对应的代理类
            if (methodFunctionProxy == null) {
                // 命名规范，[子类]ServiceImp.java由工具自动生成
                Class<?> cls = Class.forName(getClass().getName() + "Impl");
                Constructor<?> c = cls.getDeclaredConstructor();
                c.setAccessible(true);
                methodFunctionProxy = (RPCImplBase)c.newInstance();
            }

            // 通过代理类 获取函数引用
            return methodFunctionProxy.getMethodFunction(this, methodKey);
        } catch (Exception e) {
            throw new SysException(e);
        }
    }


    @Override
    public void onClose(){

        node.remove(this);

        // 回收
        for (Map.Entry<String, CallPulseBuffer> en : callFrameBuffers.entrySet()){
            en.getValue().close();
        }
        callFrameBuffers.clear();
    }

    /**
     * 获取系统时间
     *
     * @return
     */
    public static long getTime() {
        return getCurrent().getTimeCurrent();
    }

}
