package org.evd.game.runtime.support.exception;

/**
 * @author zhuxiaoqing
 * @Description: InboundBusinessException
 * @Date 2026/7/10 14:22
 **/

public final class InboundBusinessException extends SysException {
    public InboundBusinessException(int errorCode, String message) {
        super(errorCode, message);
    }
}
