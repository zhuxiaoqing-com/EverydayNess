package org.evd.game.runtime.support;

/**
 * 
 * 
 * 当程序出现系统级错误，不希望用户看到出错信息的时候抛出此异常
 */
public class SysException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final int errorCode;

	public SysException(int errorCode) {
		this.errorCode = errorCode;
	}

	public SysException(String str) {
		this(RpcErrorCodes.UNKNOWN, str);
	}

	public SysException(int errorCode, String str) {
		super(str);
		this.errorCode = errorCode;
	}
	
	public SysException(Throwable e) {
		this(RpcErrorCodes.UNKNOWN, e);
	}

	public SysException(int errorCode, Throwable e) {
		super(e);
		this.errorCode = errorCode;
	}
	
	public SysException(Throwable e, String str) {
		this(RpcErrorCodes.UNKNOWN, e, str);
	}

	public SysException(int errorCode, Throwable e, String str) {
		super(str, e);
		this.errorCode = errorCode;
	}
	
	public SysException(String str, Object...params) {
		this(RpcErrorCodes.UNKNOWN, str, params);
	}

	public SysException(int errorCode, String str, Object...params) {
		super(RuntimeUtils.createStr(str, params));
		this.errorCode = errorCode;
	}
	
	public SysException(Throwable e, String str, Object...params) {
		this(RpcErrorCodes.UNKNOWN, e, str, params);
	}

	public SysException(int errorCode, Throwable e, String str, Object...params) {
		super(RuntimeUtils.createStr(str, params), e);
		this.errorCode = errorCode;
	}

	public int getErrorCode() {
		return errorCode;
	}
}
