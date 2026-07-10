package org.evd.game.runtime.continuation;

/**
 * 协程等待的业务类型。
 */
public enum WaitType {
    /** 通用超时等待。 */
    GENERIC,
    /** 显式 sleep 等待。 */
    SLEEP,
    /** 协程锁等待。 */
    LOCK,
    /** CompletionStage 异步结果等待。 */
    COMPLETION_STAGE,
    /** 远程 RPC 结果等待。 */
    RPC
}
