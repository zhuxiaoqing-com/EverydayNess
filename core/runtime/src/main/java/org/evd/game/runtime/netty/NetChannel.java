package org.evd.game.runtime.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Attribute;
import io.netty.util.ReferenceCountUtil;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.client.ClientSessionRef;
import org.evd.game.runtime.support.LogCore;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final ClientSessionRef sessionRef;
    private String pendingLoginToken = "";
    private String userId = "";
    private volatile SessionState sessionState = SessionState.CONNECTED;
    private final AtomicBoolean closeCleanupStarted = new AtomicBoolean();

    /** 对账异常计数属于当前 GW Session；Session 被替换时随 NetChannel 一起消失。 */
    private int onlineMissingCount;


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
    }


    public long getPlayerId() {
        return sessionRef.getPlayerId();
    }

    public String getPendingLoginToken() {
        return pendingLoginToken;
    }

    public void setPendingLoginToken(String pendingLoginToken) {
        this.pendingLoginToken = pendingLoginToken == null ? "" : pendingLoginToken;
    }

    public void setPlayerId(long playerId) {
        sessionRef.setPlayerId(playerId);
    }

    public CallPoint getGate() {
        return sessionRef.getGate();
    }

    public void setGate(CallPoint gate) {
        sessionRef.setGate(gate);
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

    /** 保证连接关闭后的离线清理只执行一次；CLOSING 可能已由关闭前响应提前设置。 */
    public boolean beginCloseCleanup() {
        return closeCleanupStarted.compareAndSet(false, true);
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
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId == null ? "" : userId;
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
        return sessionState == SessionState.USER_LOGIN_READY
                || sessionState == SessionState.PLAYER_LOGIN_READY;
    }

    /** 记录当前 Session 与 Online 的连续对账异常；返回值表示已连续发现两轮。 */
    public boolean observeOnlineReconcileMismatch() {
        return ++onlineMissingCount >= 2;
    }

    public void clearOnlineReconcileMismatch() {
        onlineMissingCount = 0;
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
        Attribute<Integer> attribute = channel.attr(ServerAttributeKey.brokenType);
        Integer oldCode;
        boolean success;
        if (brokenType == null || brokenType == BrokenType.NONE) {
            oldCode = attribute.get();
            success = false;
        } else {
            oldCode = attribute.setIfAbsent(brokenType.getCode());
            success = oldCode == null;
        }
        BrokenType current = BrokenType.fromCode(attribute.get());
        LogCore.core.info("NetChannel 设置断开原因: sessionId={}, success={}, requested={}, old={}, current={}",
                channelId, success, brokenType, BrokenType.fromCode(oldCode), current);
    }

    public Set<Integer> getMergedIds() {
        return mergedIds;
    }

    private static String remoteAddress(SocketAddress remoteAddress) {
        return remoteAddress == null ? "unknown" : remoteAddress.toString();
    }

    public enum SessionState {
        /** TCP 已建立，等待首段登录或二段登录协议。 */
        CONNECTED(Set.of(
                CMD_LOGIN,
                CMD_LOGIN2,
                CMD_CONN_PING
        )),
        /** user 已完成正式登录，客户端等待选择 playerId。 */
        USER_LOGIN_READY(Set.of(
                CMD_CREATE_ROLE,
                CMD_SELECT_ROLE_ENTER,
                CMD_CONN_PING
        )),
        /** playerId 已完成登录，允许全部客户端协议进入业务路由，但禁止重复登录流程协议。 */
        PLAYER_LOGIN_READY(null, Set.of(
                CMD_LOGIN,
                CMD_LOGIN2,
                CMD_CREATE_ROLE,
                CMD_SELECT_ROLE_ENTER
        )),
        /** 连接正在关闭，拒绝后续客户端协议。 */
        CLOSING(Set.of()),

        ;
        private final Set<Integer> allowedCmds;
        private final Set<Integer> disallowedCmds;

        SessionState(Set<Integer> allowedCmds) {
            this(allowedCmds, Set.of());
        }

        SessionState(Set<Integer> allowedCmds, Set<Integer> disallowedCmds) {
            this.allowedCmds = allowedCmds;
            this.disallowedCmds = disallowedCmds;
        }

        boolean canProcess(int msgId) {
            if (disallowedCmds != null && disallowedCmds.contains(msgId)) {
                return false;
            }

            if(allowedCmds!= null && !allowedCmds.contains(msgId)) {
                return false;
            }
            return true;
        }
    }
}
