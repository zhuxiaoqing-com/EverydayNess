package org.evd.game.runtime.Db.table;

/**
 * 简单的定时器状态管理
 * 
 * 包装 nextTime、running 和 interval 三个字段，提供简单的状态管理方法
 */
public class TimerState {
    private long nextTime;
    private boolean running;
    private long interval;
    
    public TimerState() {
        this.nextTime = -1;
        this.running = false;
        this.interval = -1;
    }
    
    public TimerState(long nextTime, boolean running, long interval) {
        this.nextTime = nextTime;
        this.running = running;
        this.interval = interval;
    }
    
    public TimerState(long interval) {
        this.interval = interval;
        this.nextTime = System.currentTimeMillis() + interval;
        this.running = false;
    }
    
    public TimerState(long startTime, long interval) {
        this.interval = interval;
        this.nextTime = startTime + interval;
        this.running = false;
    }
    
    /**
     * 检查是否可以执行下一次任务
     * @param currentTime 当前时间
     * @return true如果可以执行，false如果不可以
     */
    public boolean canExecute(long currentTime) {
        return !running && interval > 0 && nextTime > 0 && currentTime >= nextTime;
    }
    
    /**
     * 标记开始执行
     */
    public void markStart() {
        this.running = true;
    }
    
    /**
     * 标记执行完成，并自动设置下一次执行时间
     */
    public void markComplete() {
        this.running = false;
        if (interval > 0) {
            this.nextTime = System.currentTimeMillis() + interval;
        }
    }
    
    /**
     * 标记执行完成，并指定下一次执行时间
     * @param nextTime 下一次执行时间
     */
    public void markComplete(long nextTime) {
        this.running = false;
        this.nextTime = nextTime;
    }
    
    /**
     * 设置下一次执行时间
     * @param nextTime 下一次执行时间
     */
    public void setNextTime(long nextTime) {
        this.nextTime = nextTime;
    }
    
    /**
     * 获取下一次执行时间
     * @return 下一次执行时间
     */
    public long getNextTime() {
        return nextTime;
    }
    
    /**
     * 检查是否正在执行
     * @return true如果正在执行，false如果不在执行
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 检查是否已经初始化
     * @return true如果已经初始化，false如果未初始化
     */
    public boolean isInitialized() {
        return interval > 0;
    }
    
    /**
     * 重置状态
     */
    public void reset() {
        this.nextTime = -1;
        this.running = false;
        this.interval = -1;
    }
    
    /**
     * 获取执行间隔
     * @return 执行间隔（毫秒）
     */
    public long getInterval() {
        return interval;
    }
    
    /**
     * 设置执行间隔
     * @param interval 执行间隔（毫秒）
     */
    public void setInterval(long interval) {
        this.interval = interval;
    }
    
    /**
     * 距离下一次执行还有多久
     * @param currentTime 当前时间
     * @return 距离下一次执行的毫秒数，如果已经过了返回0
     */
    public long getTimeUntilNext(long currentTime) {
        if (!isInitialized() || nextTime <= 0) {
            return 0;
        }
        return Math.max(0, nextTime - currentTime);
    }
}