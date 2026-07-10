package org.evd.game.runtime.support.exception;

import org.evd.game.runtime.support.RpcErrorCodes;

public class ServiceStoppingException extends SysException {
    public ServiceStoppingException() {
        super(RpcErrorCodes.SERVICE_STOPPING);
    }

    public ServiceStoppingException(String message) {
        super(RpcErrorCodes.SERVICE_STOPPING, message);
    }

    public ServiceStoppingException(Throwable cause, String message) {
        super(RpcErrorCodes.SERVICE_STOPPING, cause, message);
    }
}
