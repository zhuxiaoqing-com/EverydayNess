package org.evd.game.runtime;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.netty.NetChannel;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RemoteNode 一次物理链路对应的会话。为了后续RPC懒加载而创建
 *
 * <p>当前严格连接模式在 Channel 建立后创建会话。Session 与 Channel 一旦绑定便不再换绑，
 * 因而旧链路断开时可以只结束旧 Session 的 RPC 等待，不会影响重连后的新链路。</p>
 */
public final class RemoteSession {
    private static final AtomicLong SESSION_ID_ALLOC = new AtomicLong();

    private final long sessionId;
    private final Node localNode;
    private final CallPoint remoteCallPoint;
    private final NetChannel channel;
    private final AtomicBoolean closed = new AtomicBoolean();

    RemoteSession(Node localNode, CallPoint remoteCallPoint, NetChannel channel) {
        this.sessionId = SESSION_ID_ALLOC.incrementAndGet();
        this.localNode = localNode;
        this.remoteCallPoint = new CallPoint(remoteCallPoint);
        this.channel = channel;
    }

    public long getSessionId() {
        return sessionId;
    }

    public int getRemoteNodeId() {
        return remoteCallPoint.nodeId;
    }

    public CallPoint getRemoteCallPoint() {
        return new CallPoint(remoteCallPoint);
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

    /** 返回 true 表示消息已交给 Netty；拒绝写入时释放 ByteBuf。 */
    boolean tryWrite(ByteBuf byteBuf) {
        if (byteBuf == null) {
            return false;
        }
        if (!isValid()) {
            ReferenceCountUtil.release(byteBuf);
            return false;
        }
        return channel.tryWrite(byteBuf);
    }

    public void close() {
        channel.close();
    }
}
