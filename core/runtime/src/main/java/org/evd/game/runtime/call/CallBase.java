package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.netty.NetChannel;

@SerializeClass
public abstract class CallBase implements ISerializable {
    /** ------------从哪来-------------*/
    @SerializeField
    public CallPoint from;
    /** ------------到哪去-------------*/
    @SerializeField
    public CallPoint to;
    /** 请求后回调contextid */
    @SerializeField
    public long id;
    /** 运行时入站连接，仅用于本进程内分发，不参与序列化。 */
    private transient NetChannel sourceChannel;
    /** 运行时出站连接标识，不参与序列化；未绑定为 -1，本地直连为 0。 */
    private transient long outboundChannelId = -1L;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public CallPoint getFrom() {
        return from;
    }

    public void setFrom(CallPoint from) {
        this.from = from;
    }

    public CallPoint getTo() {
        return to;
    }

    public void setTo(CallPoint to) {
        this.to = to;
    }

    public NetChannel getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(NetChannel sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public long getOutboundChannelId() {
        return outboundChannelId;
    }

    public void setOutboundChannelId(long outboundChannelId) {
        this.outboundChannelId = outboundChannelId;
    }
}
