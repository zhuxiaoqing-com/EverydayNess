package org.evd.game.runtime;

import jdk.internal.vm.ContinuationScope;
import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallBase;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.call.CallResult;
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
    /** 协程池 */
    private final ContinuationPool continuationPool = new ContinuationPool(this);
    /** id分配器 */
    private long conIdAlloc = 1;
    private long applyConId(){return conIdAlloc++;}
    /** rpc等待id分配器 */
    private long waitIdAlloc = 1;
    private long applyWaitId(){return waitIdAlloc++;}
    /** 当前正在执行的写成 */
    private Task.ContinuationWrapper runningContinuation;
    /** 执行中和阻塞的协程 */
    private final Map<Long, Task.ContinuationWrapper> continuations = new HashMap<>();
    /** 当前处于等待态的协程 */
    private final Map<Long, WaitContext> waitContexts = new HashMap<>();
    /** 本帧超时的等待id */
    private final List<Long> timeoutWaitIds = new ArrayList<>();
    /** ThreadLocal */
    private final static ThreadLocal<Service> threadLocal = new ThreadLocal<>();
    public static Service getCurrent(){
        return threadLocal.get();
    }
    @FunctionalInterface
    private interface WaitTimeoutHandler {
        void onTimeout(Task.ContinuationWrapper continuation, long waitId);
    }
    private static class WaitContext {
        private final Task.ContinuationWrapper continuation;
        private final long deadline;
        private final WaitTimeoutHandler timeoutHandler;

        private WaitContext(Task.ContinuationWrapper continuation, long deadline, WaitTimeoutHandler timeoutHandler) {
            this.continuation = continuation;
            this.deadline = deadline;
            this.timeoutHandler = timeoutHandler;
        }

        private boolean isTimeout(long now) {
            return deadline > 0 && deadline <= now;
        }
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
        // 申请一个协程
        Task.ContinuationWrapper continuation = continuationPool.apply();
        // 绑定行为
        continuation.bindTask(new Task.TaskParam0(this::init), applyConId());
        // 设置为当前正在执行
        runningContinuation = continuation;
        try {
            // 执行协程
            continuation.runVirtual();
        } finally {
            // 取消正在执行
            this.runningContinuation = null;
        }
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
        // 申请一个协程
        Task.ContinuationWrapper context = continuationPool.apply();
        // 绑定行为
        context.bindTask(new Task.TaskParam0(this::tick), applyConId());
        // 设置为当前正在执行
        runningContinuation = context;
        try {
            // 执行协程
            context.runVirtual();
        } finally {
            this.runningContinuation = null;
        }
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
        if (waitContexts.isEmpty()) {
            return;
        }
        long now = getTimeCurrent();
        for (Map.Entry<Long, WaitContext> entry : waitContexts.entrySet()) {
            if (entry.getValue().isTimeout(now)) {
                timeoutWaitIds.add(entry.getKey());
            }
        }
        try {
            for (Long waitId : timeoutWaitIds) {
                WaitContext waitContext = waitContexts.remove(waitId);
                if (waitContext == null) {
                    continue;
                }
                waitContext.timeoutHandler.onTimeout(waitContext.continuation, waitId);
                resumeContinuation(waitContext.continuation);
            }
        } finally {
            timeoutWaitIds.clear();
        }
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
    }

    private void dispatchCall_st(CallBase callbase) {
        Task.ContinuationWrapper context;
        // 发送的call
        if (callbase instanceof Call call){
            // 申请一个协程
            context = continuationPool.apply();
            // 绑定行为
            context.bindTask(new Task.TaskParam1<>(this::dispatch_st, call), applyConId());
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
        resumeContinuation(context);
    }

    /**
     * 派发到对应的rpc监听函数
     * @param call
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch_st(Call call){
        Object func = getMethodFunction(call.methodKey);
        Object[] m = call.methodParam;
        if (call.needResult){
            CallResult callReturn = call.createReturn();
            try {
                Object result = null;
                switch (call.methodParam.length) {
                    case 0: result = ((ReturnFunction0) func).apply(); break;
                    case 1: result = ((ReturnFunction1) func).apply(m[0]); break;
                    case 2: result = ((ReturnFunction2) func).apply(m[0], m[1]); break;
                    case 3: result = ((ReturnFunction3) func).apply(m[0], m[1], m[2]); break;
                    case 4: result = ((ReturnFunction4) func).apply(m[0], m[1], m[2], m[3]); break;
                    case 5: result = ((ReturnFunction5) func).apply(m[0], m[1], m[2], m[3], m[4]); break;
                    case 6: result = ((ReturnFunction6) func).apply(m[0], m[1], m[2], m[3], m[4], m[5]); break;
                    case 7: result = ((ReturnFunction7) func).apply(m[0], m[1], m[2], m[3], m[4], m[5], m[6]); break;
                    case 8: result = ((ReturnFunction8) func).apply(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7]); break;
                    case 9: result = ((ReturnFunction9) func).apply(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8]); break;
                    case 10: result = ((ReturnFunction10) func).apply(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9]); break;
                    default: break;
                }
                callReturn.result = result;
            } catch (Throwable e) {
                LogCore.core.error("rpc dispatch failed: service={}, methodKey={}", id, call.methodKey, e);
                fillRpcFailure(callReturn, e);
            }
            sendCall_st(callReturn);
        }else{
            try {
                switch (call.methodParam.length) {
                    case 0: ((Function0) func).apply(); break;
                    case 1: ((Function1) func).apply(m[0]); break;
                    case 2: ((Function2) func).apply(m[0], m[1]); break;
                    case 3: ((Function3) func).apply(m[0], m[1], m[2]); break;
                    case 4: ((Function4) func).apply(m[0], m[1], m[2], m[3]); break;
                    case 5: ((Function5) func).apply(m[0], m[1], m[2], m[3], m[4]); break;
                    case 6: ((Function6) func).apply(m[0], m[1], m[2], m[3], m[4], m[5]); break;
                    case 7: ((Function7) func).apply(m[0], m[1], m[2], m[3], m[4], m[5], m[6]); break;
                    case 8: ((Function8) func).apply(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7]); break;
                    case 9: ((Function9) func).apply(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8]); break;
                    case 10: ((Function10) func).apply(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9]); break;
                    default: break;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public void holdContinuation(Task.ContinuationWrapper conTask){
        continuations.put(conTask.getConId(), conTask);
    }
    public void unHoldContinuation(Task.ContinuationWrapper conTask){
        continuations.remove(conTask.getConId());
        // 回收
        continuationPool.recycle(conTask);
    }

    /**
     * 创建call请求，并发送到目标service
     * 针对不需要返回结果的call请求
     * @param toCallPoint
     * @param methodKey
     * @param params
     */
    public void call(CallPoint toCallPoint, int methodKey, Object[] params) {
        Call call = new Call();
        call.from = this.callPoint;
        call.to = toCallPoint;

        call.methodKey = methodKey;
        call.methodParam = params;

        sendCall_st(call);
    }

    /**
     * 创建call请求，并发送到目标service
     * 针对需要返回结果的call请求
     * @param toCallPoint
     * @param methodKey
     * @param params
     */
    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params) {
        return callWait(toCallPoint, methodKey, params, getCallWaitTimeout());
    }

    /**
     * 创建call请求，并发送到目标service
     * 针对需要返回结果的call请求
     * @param toCallPoint
     * @param methodKey
     * @param params
     * @param timeoutMillis 超时时间，单位毫秒，小于等于0代表不启用超时
     */
    public Object callWait(CallPoint toCallPoint, int methodKey, Object[] params, long timeoutMillis) {
        Task.ContinuationWrapper thisContinuation = requireRunningContinuation();
        long waitId = registerWait(thisContinuation, timeoutMillis,
                (continuation, timeoutWaitId) -> continuation.setFailure(
                        new SysException("rpc call timeout: service={}, waitId={}", id, timeoutWaitId)));

        Call call = new Call();
        call.from = this.callPoint;
        call.to = toCallPoint;

        call.id = waitId;

        call.methodKey = methodKey;
        call.methodParam = params;

        call.needResult = true;

        if (!sendCall_st(call)) {
            waitContexts.remove(waitId);
            throw new SysException("send rpc call failed: service={}, toNode={}, toService={}, methodKey={}",
                    id, toCallPoint.nodeId, toCallPoint.servId, methodKey);
        }

        // 等待结果，内部会阻塞当前协程，直到call请求的结果返回
        return thisContinuation.waitResult();
    }

    /**
     * 协程等待指定时间，常用于需要显式超时点的业务逻辑
     */
    public void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        Task.ContinuationWrapper thisContinuation = requireRunningContinuation();
        registerWait(thisContinuation, delayMillis, (continuation, waitId) -> continuation.setResult(null));
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
        Task.ContinuationWrapper thisContinuation = runningContinuation;
        if (thisContinuation == null) {
            throw new SysException("continuation wait must run inside continuation: service={}", id);
        }
        return thisContinuation;
    }

    private long registerWait(Task.ContinuationWrapper continuation, long timeoutMillis, WaitTimeoutHandler timeoutHandler) {
        long waitId = applyWaitId();
        long deadline = timeoutMillis > 0 ? getWaitBaseTime() + timeoutMillis : -1L;
        continuation.prepareWait();
        waitContexts.put(waitId, new WaitContext(continuation, deadline, timeoutHandler));
        return waitId;
    }

    private Task.ContinuationWrapper takeWaitContinuation(long waitId) {
        WaitContext waitContext = waitContexts.remove(waitId);
        return waitContext == null ? null : waitContext.continuation;
    }

    private void resumeContinuation(Task.ContinuationWrapper continuation) {
        runningContinuation = continuation;
        try {
            continuation.runVirtual();
        } finally {
            runningContinuation = null;
        }
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

