package org.evd.game.runtime.support;

/**
 * RPC 与运行时异常的统一错误码定义。
 */
public final class RpcErrorCodes {
    public static final int UNKNOWN = 1; // 未知错误，未命中更具体的错误分类时使用
    public static final int ACTOR_NOT_FOUND = 2; // 目标 Actor 不存在，或无法定位到对应地址
    public static final int ACTOR_TYPE_MISMATCH = 4; // 目标 Actor 存在，但实际类型与期望类型不一致
    public static final int RPC_CALL_TIMEOUT = 5; // 普通 RPC 调用超时
    public static final int ACTOR_RPC_CALL_TIMEOUT = 6; // Actor 定向 RPC 调用超时
    public static final int COROUTINE_LOCK_TIMEOUT = 7; // 协程锁等待超时
    public static final int SERVICE_STOPPING = 8; // service关闭
    public static final int RPC_TRANSPORT_UNAVAILABLE = 9; // 节点链路不可用或写失败
    public static final int RPC_BACKPRESSURED = 10; // 出站队列或 Netty 写缓冲达到上限
    public static final int SERVICE_NOT_FOUND = 11; // 对面找不到service
    public static final int SERVICE_NOT_READY = 12; // 对面service还在初始化状态

    private RpcErrorCodes() {
    }
}
