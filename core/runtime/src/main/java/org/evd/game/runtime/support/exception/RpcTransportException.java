package org.evd.game.runtime.support.exception;

import org.evd.game.runtime.support.RpcErrorCodes;
import org.evd.game.runtime.call.CallPoint;

/** RPC 尚未成功交给远端连接，或连接在发送过程中失效。 */
public final class RpcTransportException extends SysException {
    private final CallPoint remoteNodePoint;
    private final long waitId;

    public RpcTransportException(CallPoint remoteNodePoint, long waitId) {
        super(RpcErrorCodes.RPC_TRANSPORT_UNAVAILABLE,
                "rpc transport unavailable: remoteNode={}, waitId={}", remoteNodePoint, waitId);
        this.remoteNodePoint = remoteNodePoint == null ? null : new CallPoint(remoteNodePoint);
        this.waitId = waitId;
    }

    public RpcTransportException(String message, Object... params) {
        super(RpcErrorCodes.RPC_TRANSPORT_UNAVAILABLE, message, params);
        this.remoteNodePoint = null;
        this.waitId = 0L;
    }

    public CallPoint getRemoteNodePoint() {
        return remoteNodePoint == null ? null : new CallPoint(remoteNodePoint);
    }

    public long getWaitId() {
        return waitId;
    }
}
