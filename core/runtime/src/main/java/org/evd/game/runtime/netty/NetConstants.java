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
    int SERVICE_SO_SEND_BUFFER_SIZE = BufferPool.BUFFER_SIZE *2;
    /** 服务器之间使用的接收缓冲区大小 */
    int SERVICE_SO_RECEIVE_BUFFER_SIZE =  BufferPool.BUFFER_SIZE *2;
    // 服务器的最大帧长度 4字节长度 + 最大2MB消息体
    int SERVICE_MAX_FRAME_LENGTH = BufferPool.BUFFER_SIZE + 4;

}
