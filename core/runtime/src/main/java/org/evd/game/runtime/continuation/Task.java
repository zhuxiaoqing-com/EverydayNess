package org.evd.game.runtime.continuation;

import jdk.internal.vm.Continuation;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.function.Function0;
import org.evd.game.runtime.support.function.Function1;

import java.io.Closeable;

public class Task {
    public enum Reason{
        RPC,
        NORMAL,
        UNLOCK,
        TIMER, ORDER_RPC;
    }

    public enum DebugState {
        NEW,
        READY,
        RUNNING,
        WAITING,
        COMPLETED
    }

    /**
     * 对协程栈的封装
     */
    public static class ContinuationWrapper implements Runnable, Closeable {
        /** service */
        private final Service service;
        /** 栈 */
        private final Continuation continuation;

        // ----- 以下的参数会变化，不同的逻辑设置为不同的task -----
        /** 执行的逻辑 */
        private Runnable task;
        /** 返回的结果 */
        private Object result;
        /** 等待失败原因 */
        private RuntimeException failure;
        /** 协程id（回调id） */
        private long conId;
        /** 当前协程所属的 actor */
        private ActorId actorId;
        /** 调试信息 */
        private ContinuationDebugInfo.DebugInfo debugInfo;
        /** 当前等待调试信息 */
        private ContinuationDebugInfo.DebugInfo waitDebugInfo;
        /** 最近一次入队列理由 */
        private Reason queueReason;
        /** 调试状态 */
        private DebugState debugState = DebugState.NEW;
        /** 当前正在运行该协程的线程 */
        private Thread runningThread;

        public ContinuationWrapper(Service service) {
            this.service = service;
            this.continuation = new Continuation(service.getScope(), this);
        }

        /**
         * 绑定task（要执行的逻辑），并执行协程id
         * @param task
         * @param conId
         */
        public void bindTask(Runnable task, long conId) {
            bindTask(task, conId, null);
        }

        public void bindTask(Runnable task, long conId, ActorId actorId) {
            this.task = task;
            this.conId = conId;
            this.actorId = actorId == null ? null : new ActorId(actorId);
            this.debugInfo = null;
            this.waitDebugInfo = null;
            this.queueReason = null;
            this.debugState = DebugState.NEW;
            this.runningThread = null;
        }

        public void bindDebugInfo(ContinuationDebugInfo.DebugInfo debugInfo) {
            this.debugInfo = debugInfo;
        }

        public void markQueued(Reason queueReason) {
            this.queueReason = queueReason;
            this.debugState = DebugState.READY;
            this.waitDebugInfo = null;
        }

        public void markRunning() {
            this.debugState = DebugState.RUNNING;
            this.runningThread = Thread.currentThread();
        }

        public void markExecutionPaused() {
            this.runningThread = null;
        }

        public void markWaiting(ContinuationDebugInfo.DebugInfo waitDebugInfo) {
            this.debugState = DebugState.WAITING;
            this.waitDebugInfo = waitDebugInfo;
        }

        public void markCompleted() {
            this.debugState = DebugState.COMPLETED;
            this.runningThread = null;
        }

        /**
         * 由协程执行
         */
        @Override
        public void run() {
            while (true) {
                doWork();
                Continuation.yield(continuation.getScope());
            }
        }

        private void doWork() {
            if (task == null){
                // TODO 警告
                return;
            }
            // 先放入service中，因为task.run()可能发生协程yield
            // 如果不保存，则无法拿到栈恢复执行
            service.holdContinuation(this);
            try {
                task.run();
            } finally {
                // 执行结束，移除
                service.unHoldContinuation(this);
            }
        }

        @Override
        public void close() {
            // 清理临时变量
            task = null;
            result = null;
            failure = null;
            conId = 0;
            actorId = null;
            debugInfo = null;
            waitDebugInfo = null;
            queueReason = null;
            debugState = DebugState.NEW;
            runningThread = null;
        }

        /**
         * 执行或继续执行run()函数中的逻辑
        */
        public void runVirtual(){
            continuation.run();
        }

        public long getConId(){
            return conId;
        }

        public void setResult(Object result){
            this.result = result;
        }

        public void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        public ContinuationDebugInfo.DebugInfo getDebugInfo() {
            return debugInfo;
        }

        public ContinuationDebugInfo.DebugInfo getWaitDebugInfo() {
            return waitDebugInfo;
        }

        public Task.Reason getQueueReason() {
            return queueReason;
        }

        public void prepareWait() {
            result = null;
            failure = null;
        }

        /**
         * 协程进入阻塞，等待结果
         * @return
         */
        public Object waitResult() {
            // 协程进入阻塞，因为此时result为null
            // 需要等其他协程setResult后并runVirtual唤醒协程，才能执行return result;
            Continuation.yield(continuation.getScope());
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        public ActorId getActorId() {
            return actorId;
        }

        public DebugState getDebugState() {
            return debugState;
        }

        /**
         * 调试时根据当前协程状态，优先返回最有意义的栈：
         * RUNNING 时抓 carrier thread 的实时线程栈，其余状态读取 continuation 挂起栈。
         */
        public StackTraceElement[] getDebugStackTrace() {
            if (debugState == DebugState.RUNNING) {
                Thread thread = runningThread;
                if (thread == null) {
                    return ContinuationDebugStackFilter.emptyStack();
                }
                return ContinuationDebugStackFilter.filter(thread.getStackTrace());
            }
            return ContinuationDebugStackFilter.filter(continuation.getStackTrace());
        }
    }

    public static class TaskParam0 implements Runnable {
        private final Function0 func;
        public TaskParam0(Function0 func) {
            this.func = func;
        }

        @Override
        public void run() {
            try {
                func.apply();
            }catch (Exception e){
                LogCore.core.error("执行TaskParam0失败", e);
            }
        }
    }

    public static class TaskParam1<T1> implements Runnable {
        private final Function1<T1> func;
        private final T1 t1;
        public TaskParam1(Function1<T1> func, T1 t1) {
            this.func = func;
            this.t1 = t1;
        }

        @Override
        public void run() {
            try {
                func.apply(t1);
            }catch (Exception e){
                LogCore.core.error("执行TaskParam1失败: param={}", t1, e);
            }
        }
    }

}
