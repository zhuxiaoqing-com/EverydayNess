package org.evd.game.runtime;

import org.evd.game.runtime.continuation.Task;
import org.evd.game.runtime.misc.FrameStatistics;
import org.evd.game.runtime.misc.ScheduledExecutor;
import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.SysException;

import java.util.concurrent.TimeUnit;

public abstract class TickCase {
    // tick间隔
    protected final static int TICK_INTERVAL = 5;
    enum CaseStatus{
        New,
        Running,
        PendingKill,
        FinishKill,
        Closed
    }

    /** 服务状态 */
    protected volatile CaseStatus status = CaseStatus.New;

    public CaseStatus getStatus() {
        return status;
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

        if (status == CaseStatus.Running || status == CaseStatus.PendingKill) {
            // 计时心跳，心跳间隔时间动态变化
            long pulseLeftTime = tickInterval - timeFrame;
            if (pulseLeftTime <= 0)
                scheduledExecutor.submit(tickTask);
            else
                scheduledExecutor.schedule(tickTask, pulseLeftTime, TimeUnit.MILLISECONDS);

            // service被停止
        } else if (status == CaseStatus.FinishKill) {
            status = CaseStatus.Closed;
            onClose();
        }
    }

    public void stop() {
        if (isStopping()) {
            LogCore.core.warn("already stop service  !!! class {} ", getClass().getSimpleName());
            return;
        }
        LogCore.core.info("stop service start !!!class {} ", getClass().getSimpleName());
        status = CaseStatus.PendingKill;
        long currTime = System.currentTimeMillis();
        try {
            onStop();
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            LogCore.core.error("stop service error!!! costMill {} class {} ", endTime - currTime, getClass().getSimpleName(), e);
        } finally {
            status = CaseStatus.FinishKill;
        }
        long endTime = System.currentTimeMillis();
        LogCore.core.info("stop service end costMill {} class {} ", endTime - currTime, getClass().getSimpleName());
    }

    /**
     * 关服逻辑要写这里，等这个方法结束就结束，协程运行
     */
    protected void onStop() {
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
        status = CaseStatus.Running;
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
