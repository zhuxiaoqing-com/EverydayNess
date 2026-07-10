package org.evd.game.runtime.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Attribute;
import io.netty.util.ReferenceCountUtil;
import org.evd.game.runtime.client.ClientSessionRef;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class NetChannel {
    private static final int CMD_LOGIN = 1001;
    private static final int CMD_CONN_PING = 1002;
    private static final int CMD_LOGIN2 = 1003;
    private static final int CMD_LOGIN3 = 1004;
    private static final int CMD_CREATE_ROLE = 1005;
    private static final int CMD_SELECT_ROLE_ENTER = 1006;

    public static final int MESSAGE_GW_TIME = 30;
    public static final int MESSAGE_GW_COUNT = 150;

    public static final int MESSAGE_BT_TIME = 30;
    public static final int MESSAGE_BT_COUNT = 200;

    /** 自增的id **/
    private static final AtomicLong autoChannelId = new AtomicLong(0);

    private Channel channel;

    /** 分配的ID */
    private long channelId;

    private final String remoteAddress;

    // 客户端和服务器之间心跳维护
    private long lastPingTime;
    // 开加速器之后心跳间隔变短, 计数, 超过20次就主动提出
    private int invalidPingCount;
    // 正常的计数, 用于清除异常次数
    private int normalPingCount;

    private long playerId;

    private String userId;

    private final ClientSessionRef sessionRef;
    private SessionState sessionState = SessionState.CONNECTED;


    private InetSocketAddress playerEnterAddress;

    private long lastMessageTime;
    private int frequentlyMessageCount;

    // 历史消息
    public List<HisMessage> frequentlyMessageList = new ArrayList<>();

    public String remoteClientIp;

    private final Set<Integer> mergedIds = new HashSet<>();


    public NetChannel(Channel channel) {
        Attribute<NetChannel> netChannel = channel.attr(ServerAttributeKey.netChannel);
        netChannel.set(this);
        Attribute<Long> attribute = channel.attr(ServerAttributeKey.channel_Id);
        this.channelId = autoChannelId.addAndGet(1);
        attribute.set(this.channelId);
        this.channel = channel;
        this.remoteAddress = remoteAddress(channel.remoteAddress());
        this.sessionRef = new ClientSessionRef();
        this.sessionRef.setSessionId(this.channelId);
        this.lastPingTime = System.currentTimeMillis();
        setBrokenType(BrokenType.NONE);
    }


    public long getPlayerId() {
        return sessionRef.getPlayerId();
    }

    public void setPlayerId(long playerId) {
        sessionRef.setPlayerId(playerId);
    }

    public SessionState getSessionState() {
        return sessionState;
    }

    public void setSessionState(SessionState sessionState) {
        this.sessionState = sessionState == null ? SessionState.CONNECTED : sessionState;
    }

    public boolean canProcessClientCmd(int msgId) {
        return sessionState.canProcess(msgId);
    }

    public boolean canWrite(int bytes) {
        Channel activeChannel = channel;
        return activeChannel != null
                && activeChannel.isActive()
                && activeChannel.isWritable()
                && activeChannel.bytesBeforeUnwritable() >= bytes;
    }

    /**
     * 只有 Netty 写缓冲仍有明确容量时才接收消息。返回 null 表示调用方没有把消息交给 Netty。
     */
    public ChannelFuture tryWrite(ByteBuf byteBuf) {
        if (byteBuf == null) {
            return null;
        }
        if (!canWrite(byteBuf.readableBytes())) {
            ReferenceCountUtil.release(byteBuf);
            return null;
        }
        return channel.writeAndFlush(byteBuf);
    }

    public boolean write(ByteBuf byteBuf) {
        return tryWrite(byteBuf) != null;
    }

    public boolean writeAndClose(ByteBuf byteBuf) {
        ChannelFuture writeFuture = tryWrite(byteBuf);
        if (writeFuture == null) {
            close();
            return false;
        }
        writeFuture.addListener(new ChannelFutureListener() {

            public void operationComplete(ChannelFuture future) throws Exception {
                future.channel().close();
            }
        });
        return true;
    }

    public void close() {
        this.channel.close();
    }

    public void close(BrokenType brokenType) {
        setBrokenType(brokenType);
        close();
    }

    public <T> T call(Object obj, Class<T> cls) {
        // Response response = new Response();
        //

        // response.waitFor();
        // return (T)response.getBody();
        return null;
    }



    public long getChannelId() {
        return channelId;
    }



    public void setChannelId(long channelId) {
        this.channelId = channelId;
        sessionRef.setSessionId(channelId);
    }

    public Channel getChannel() {
        return channel;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public InetSocketAddress getPlayerEnterAddress() {
        return playerEnterAddress;
    }

    public void setPlayerEnterAddress(InetSocketAddress playerEnterAddress) {
        this.playerEnterAddress = playerEnterAddress;
    }

    public static AtomicLong getAutochannelid() {
        return autoChannelId;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getUserId() {
        return sessionRef.getUserId();
    }

    public void setUserId(String userId) {
        sessionRef.setUserId(userId);
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public void increasedInvalidPingCount() {
        this.invalidPingCount++;
        this.normalPingCount = 0;
    }

    public void increasedNormalPingCount() {
        this.normalPingCount++;
        if (this.normalPingCount > 5) {
            this.invalidPingCount = 0;
            this.normalPingCount = 0;
        }
    }

    public int getInvalidPingCount() {
        return invalidPingCount;
    }

    public int getFrequentlyMessageCount() {
        return frequentlyMessageCount;
    }

    public void setFrequentlyMessageCount(int frequentlyMessageCount) {
        this.frequentlyMessageCount = frequentlyMessageCount;
    }

    public boolean isValid() {
        return channel != null && channel.isActive();
    }

    public long getLastPingTime() {
        return lastPingTime;
    }

    public void setLastPingTime(long lastPingTime) {
        this.lastPingTime = lastPingTime;
    }

    public boolean isAuthorized() {
        return sessionRef.isAuthorized();
    }

    public void setAuthorized(boolean authorized) {
        sessionRef.setAuthorized(authorized);
    }

    public ClientSessionRef getSessionRef() {
        return sessionRef;
    }

    public List<HisMessage> getFrequentlyMessageList() {
        return frequentlyMessageList;
    }

    public String getRemoteClientIp() {
        return remoteClientIp;
    }

    public void setRemoteClientIp(String remoteClientIp) {
        this.remoteClientIp = remoteClientIp;
    }

    public BrokenType getBrokenType() {
        return BrokenType.fromCode(channel.attr(ServerAttributeKey.brokenType).get());
    }

    public int getBrokenTypeCode() {
        return getBrokenType().getCode();
    }

    public void setBrokenType(BrokenType brokenType) {
        channel.attr(ServerAttributeKey.brokenType).set(
                (brokenType == null ? BrokenType.NONE : brokenType).getCode());
    }

    public Set<Integer> getMergedIds() {
        return mergedIds;
    }

    private static String remoteAddress(SocketAddress remoteAddress) {
        return remoteAddress == null ? "unknown" : remoteAddress.toString();
    }

    public enum SessionState {
        CONNECTED(Set.of(
                CMD_LOGIN,
                CMD_LOGIN2
        )),
        SELECT_ROLE_READY(Set.of(
                CMD_CREATE_ROLE,
                CMD_SELECT_ROLE_ENTER,
                CMD_CONN_PING
        )),
        LOGIN_READY(Set.of(
                CMD_LOGIN3,
                CMD_CONN_PING
        ));

        private final Set<Integer> allowedCmds;

        SessionState(Set<Integer> allowedCmds) {
            this.allowedCmds = allowedCmds;
        }

        boolean canProcess(int msgId) {
            return allowedCmds.contains(msgId);
        }
    }
}
