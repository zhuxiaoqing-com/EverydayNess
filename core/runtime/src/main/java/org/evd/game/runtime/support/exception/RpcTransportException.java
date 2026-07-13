package org.evd.game.runtime.support.exception;

import org.evd.game.runtime.support.RpcErrorCodes;

/** RPC 尚未成功交给远端连接，或连接在发送过程中失效。 */
public final class RpcTransportException extends SysException {
    private final String remoteNodeId;
    private final long waitId;

    public RpcTransportException(String remoteNodeId, long waitId) {
        super(RpcErrorCodes.RPC_TRANSPORT_UNAVAILABLE,
                "rpc transport unavailable: remoteNode={}, waitId={}", remoteNodeId, waitId);
        this.remoteNodeId = remoteNodeId;
        this.waitId = waitId;
    }

    public RpcTransportException(String message, Object... params) {
        super(RpcErrorCodes.RPC_TRANSPORT_UNAVAILABLE, message, params);
        this.remoteNodeId = null;
        this.waitId = 0L;
    }

    public String getRemoteNodeId() {
        return remoteNodeId;
    }

    public long getWaitId() {
        return waitId;
    }
}
