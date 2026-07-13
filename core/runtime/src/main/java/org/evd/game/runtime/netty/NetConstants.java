package org.evd.game.runtime.netty;

import org.evd.game.runtime.misc.BufferPool;

/**
 * 网络常量;
 */
public interface NetConstants {
    /** 发送缓冲区大小 */
    int SO_SEND_BUFFER_SIZE = 32 * 1024;
    /** 接收缓冲区大小 */
    int SO_RECEIVE_BUFFER_SIZE = 32 * 1024;
    /** 最大帧大小 */
    int MAX_FRAME_LENGTH = 32 * 1024 + 8;
    /** 连接超时时间 */
    int CONNECT_TIMEOUT_MILLIS = 500;

    /** 服务器之间使用的发送缓冲区大小 */
    int SERVICE_SO_SEND_BUFFER_SIZE = BufferPool.BUFFER_SIZE * 2;
    /** 服务器之间使用的接收缓冲区大小 */
    int SERVICE_SO_RECEIVE_BUFFER_SIZE =  BufferPool.BUFFER_SIZE * 2;
    // 服务器的最大帧长度 4字节长度 + 最大2MB消息体
    int SERVICE_MAX_FRAME_LENGTH = BufferPool.BUFFER_SIZE + 4;

    // 服务间连接：写缓冲区低水位，4 MB；仅用于 Netty isWritable 的最后保护。
    int SERVICE_WRITE_LOW_WATER_MARK = BufferPool.BUFFER_SIZE * 2;
    // 服务间连接：写缓冲区高水位，8 MB；不再由水位线直接断开连接。
    int SERVICE_WRITE_HIGH_WATER_MARK = BufferPool.BUFFER_SIZE * 4;

    // Node Service 状态超过该积压量后，发送方停止投递新的业务 RPC。
    int SERVICE_STATUS_BUSY_BACKLOG = 50_000;
    // 超过该时间没有收到远端状态，发送方将远端视为不可用。
    long SERVICE_STATUS_TIMEOUT_MILLIS = 9_000L;

    // 客户端连接：写缓冲区低水位，256 KB。
    // 当待发送数据降到 256 KB 以下时，Channel 会恢复为可写状态。
    int CLIENT_WRITE_LOW_WATER_MARK = 256 * 1024;
    // 客户端连接：写缓冲区高水位，1 MB。
    // 当待发送数据超过 1 MB 时，Channel 会变为不可写状态。
    int CLIENT_WRITE_HIGH_WATER_MARK = 1024 * 1024;

}
