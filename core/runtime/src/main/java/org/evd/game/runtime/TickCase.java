package org.evd.game.runtime;

import org.evd.game.runtime.call.CallServiceStopResult;
import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.misc.FrameStatistics;
import org.evd.game.runtime.misc.ScheduledExecutor;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.SysException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public abstract class TickCase {
    // tick间隔
    protected final static int TICK_INTERVAL = 5;
    enum CaseStatus{
        New,
        Starting,
        Running,
        PendingKill,
        FinishKill,
        Closed
    }

    /** 服务状态 */
    protected volatile CaseStatus status = CaseStatus.New;
    /** 在 stop 与 onClose 完整执行后完成，供 Service 外部等待真实关闭结果。 */
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    /** onStop 的失败会在 onClose 后反馈给外部等待者。 */
    private volatile RuntimeException stopFailure;

    public CaseStatus getStatus() {
        return status;
    }

    /**
     * 返回真实关闭完成信号：仅当 onStop 与 onClose 均正常返回后才成功完成。
     * 该信号必须由 Service 外部等待，Service 自己在关闭过程中不能依赖它恢复协程。
     */
    public final CompletionStage<Void> closeFuture() {
        return closeFuture;
    }

    protected final String id;
    protected long timeCurrent;
    /** tick任务，因为tick要在协程中执行，所有封装为task */
    private final Task.TaskParam0 tickTask = new Task.TaskParam0 (this::pulseCase_t);
    /** 统计帧频 */
    protected final FrameStatistics frame = new FrameStatistics(this);
    /** 调度器 */
    private ScheduledExecutor scheduledExecutor;
    private final long tickInterval;
    public TickCase(String name, long tickInterval){
        this.id = name;
        this.tickInterval = Math.max(TICK_INTERVAL, tickInterval);
    }
    /**
     * 当前线程开始时间(毫秒)
     */
    public long getTimeCurrent() {
        return timeCurrent;
    }

    public void pulseCase_t(){
        timeCurrent = System.currentTimeMillis();

        pulse();

        long timeFinish = System.currentTimeMillis();

        long timeFrame = timeFinish - timeCurrent;

        // 统计时间
        frame.tick_t(timeFinish, timeFrame);

        if (status == CaseStatus.Starting || status == CaseStatus.Running || status == CaseStatus.PendingKill) {
            // 计时心跳，心跳间隔时间动态变化
            long pulseLeftTime = tickInterval - timeFrame;
            if (pulseLeftTime <= 0)
                scheduledExecutor.submit(tickTask);
            else
                scheduledExecutor.schedule(tickTask, pulseLeftTime, TimeUnit.MILLISECONDS);

            // service被停止
        } else if (status == CaseStatus.FinishKill) {
            status = CaseStatus.Closed;
            try {
                onClose();
                if (stopFailure == null) {
                    closeFuture.complete(null);
                } else {
                    closeFuture.completeExceptionally(stopFailure);
                }
            } catch (RuntimeException | Error e) {
                closeFuture.completeExceptionally(e);
                throw e;
            }
        }
    }

    public final CallServiceStopResult rpcStop() {
        // 这里基本不会进来,service不是running的情况下，任何主动rpc调用都没会被拦截掉; 具体看这个方法：org.evd.game.runtime.Node.callHandle_snt
        if (isStopping()) {
            return new CallServiceStopResult(true, "关服中");
        }
        stop(false);
        return new CallServiceStopResult(true, "success");
    }

    public final void stop(boolean force) {
        if (isStopping()) {
            LogCore.core.warn("already stop service  !!! id {}  force {} ", getId(), force);
            return;
        }
        LogCore.core.info("stop service start !!!id {}  force {}", getId(), force);
        status = CaseStatus.PendingKill;
        long currTime = System.currentTimeMillis();
        boolean success = false;
        try {
            onStopInternal(force);
            success = true;
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            LogCore.core.error("stop service error!!! costMill {} id {} force {}", endTime - currTime, getId(), force, e);
                SysException sysException = new SysException(e, "停止服务失败: {} force {}", id);
            if (force) {
                stopFailure = sysException;
            }else{
                status = CaseStatus.Running;
                throw sysException;
            }
        } finally {
            if (force || success) {
                status = CaseStatus.FinishKill;
            }
        }
        long endTime = System.currentTimeMillis();
        LogCore.core.info("stop service end costMill {} id {}  force {} success {}", endTime - currTime, getId(), force, success);
    }

    /**
     * 关服逻辑要写这里，等这个方法结束就结束，协程运行
     */
    protected void onStopInternal(boolean force) {
    }


    protected void onClose() {
    }

    /**
     * 是否是结束状态
     */
    protected boolean isStopping() {
        return switch (status) {
            case PendingKill, FinishKill, Closed -> true;
            default -> false;
        };
    }


    public final void start(){
        // 不能重复启动
        if (status != CaseStatus.New){
            throw new SysException("node已经运行过");
        }
        if (scheduledExecutor == null){
            throw new SysException("[{}] start error, because scheduledExecutor is null", id);
        }
        status = CaseStatus.Starting;
        onStart();

        // 提交task，task中会添加并启动service
        scheduledExecutor.submit(new Task.TaskParam0(this::initCase_t));
    }
    private void initCase_t(){
        init_t();
        scheduledExecutor.submit(tickTask);
    }
    protected void init_t(){

    }
    /**
     * init由协程执行，交给子类继承
     */
    public void _init(){

    }

    /**
     * 绑定调度器
     * @param scheduledExecutor
     */
    public void bindScheduledExecutor(ScheduledExecutor scheduledExecutor) {
        if (this.scheduledExecutor != null){
            LogCore.core.warn("[{}]服务已经绑定了[{}]调度器，不能再次绑定[{}]", id, this.scheduledExecutor.getName(), scheduledExecutor.getName());
            return;
        }
        this.scheduledExecutor = scheduledExecutor;
    }

    public boolean isRunning() {
        return status == CaseStatus.Running;
    }

    /** 初始化成功后进入可提供业务的运行状态。 */
    protected final void markRunning() {
        if (status != CaseStatus.Starting) {
            throw new SysException("cannot mark running from status: {} id: {}", status, id);
        }
        status = CaseStatus.Running;
    }

    /**
     * 获取名字
     */
    public String getId(){
        return id;
    }

    protected abstract void pulse();
    protected void onStart(){

    }
}
