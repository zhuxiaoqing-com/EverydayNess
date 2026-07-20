package org.evd.game.runtime;

import org.evd.game.runtime.netty.NetChannel;

import java.util.concurrent.atomic.AtomicLong;

/**
 * RemoteNode 一次物理链路对应的会话。为了后续RPC懒加载而创建
 *
 * <p>当前严格连接模式在 Channel 建立后创建会话。Session 与 Channel 一旦绑定便不再换绑，
 * 因而旧链路断开时可以只结束旧 Session 的 RPC 等待，不会影响重连后的新链路。</p>
 */
public final class RemoteSession {
    private static final AtomicLong SESSION_ID_ALLOC = new AtomicLong();

    private final long sessionId;
    private final String remoteNodeId;
    private final NetChannel channel;

    RemoteSession(String remoteNodeId, NetChannel channel) {
        this.sessionId = SESSION_ID_ALLOC.incrementAndGet();
        this.remoteNodeId = remoteNodeId;
        this.channel = channel;
    }

    public long getSessionId() {
        return sessionId;
    }

    public String getRemoteNodeId() {
        return remoteNodeId;
    }

    public long getChannelId() {
        return channel.getChannelId();
    }

    public NetChannel getChannel() {
        return channel;
    }

    public boolean matches(NetChannel netChannel) {
        return channel == netChannel;
    }

    public boolean isValid() {
        return channel.isValid();
    }
}
