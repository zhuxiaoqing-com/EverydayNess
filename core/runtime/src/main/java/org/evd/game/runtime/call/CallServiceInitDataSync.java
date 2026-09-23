package org.evd.game.runtime.call;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.runtime.ymlconfig.RegisteredService;

/** Service 之间的初始化数据同步通知消息。 */
@SerializeClass
public final class CallServiceInitDataSync extends CallBase {
    /**
     * 发送方视角下的目标 Service 注册快照。
     *
     * <p>包含目标 Service 的 CallPoint、serviceInstanceId 和 sessionId。
     * 接收端使用它校验当前目标是否仍然是发送时的同一个 Service 实例和连接代次。
     * 如果目标 Service 已重启或 Node 已重连，则拒绝本次初始化消息。</p>
     */
    private RegisteredService targetService;

    /**
     * 发送方 Service 本次启动实例的唯一 ID。
     *
     * <p>Service 重启后必须变化，用于接收端识别并丢弃旧 Service 实例
     * 在网络延迟期间发出的初始化消息。</p>
     */
    private long sourceServiceInstanceId;

    /**
     * 发送方 Node 到目标 Node 的连接代次。
     *
     * <p>发送方服务快照中的 sessionId 使用同一个值。它用于描述这条初始化数据
     * 是在哪一次 Node 连接上产生的；同一个 Service 重连后，该值会变化。</p>
     */
    private long sourceSessionId;

    public RegisteredService getTargetService() {
        return targetService;
    }

    public void setTargetService(RegisteredService targetService) {
        this.targetService = targetService;
    }

    public long getSourceServiceInstanceId() {
        return sourceServiceInstanceId;
    }

    public void setSourceServiceInstanceId(long sourceServiceInstanceId) {
        this.sourceServiceInstanceId = sourceServiceInstanceId;
    }

    public long getSourceSessionId() {
        return sourceSessionId;
    }

    public void setSourceSessionId(long sourceSessionId) {
        this.sourceSessionId = sourceSessionId;
    }

    @Override
    public String toString() {
        return "CallServiceInitDataSync{" +
                "from=" + from +
                ", to=" + to +
                ", targetService=" + targetService +
                ", sourceServiceInstanceId=" + sourceServiceInstanceId +
                ", sourceSessionId=" + sourceSessionId +
                '}';
    }
}
