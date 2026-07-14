package org.evd.game.runtime.rpcProxyInterface;

import org.evd.game.runtime.support.exception.SysException;

import java.util.function.Supplier;

/**
 * RPC 等待调用的显式结果。
 *
 * <p>带返回值 RPC 可通过 {@link #call(Supplier)} 包装；无返回值 RPC 可通过
 * {@link #run(Runnable)} 包装。两者都会将可预期失败转换为结果，编程错误仍按异常抛出。</p>
 */
public final class RpcResult<T> {
    private static final RpcResult<Void> VOID_SUCCESS = new RpcResult<>(null, 0, null);

    private final T value;
    private final int errorCode;
    private final String errorMessage;

    private RpcResult(T value, int errorCode, String errorMessage) {
        this.value = value;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static <T> RpcResult<T> success(T value) {
        return new RpcResult<>(value, 0, null);
    }

    /**
     * 返回无返回值 RPC 的成功结果。
     *
     * <p>Void 成功结果没有状态差异，因此复用单例，避免每次调用分配对象。</p>
     */
    public static RpcResult<Void> voidSuccess() {
        return VOID_SUCCESS;
    }

    public static <T> RpcResult<T> failure(int errorCode, String errorMessage) {
        return new RpcResult<>(null, errorCode, errorMessage);
    }

    static <T> RpcResult<T> fromFailure(SysException failure) {
        return failure(failure.getErrorCode(), failure.getMessage());
    }

    /**
     * 执行一个已有的 RPC 代理调用，并将其可预期失败包装为结果。
     *
     * <p>该方法运行在调用方当前协程内，因此 RPC 挂起与恢复语义不变。</p>
     */
    public static <T> RpcResult<T> call(Supplier<T> rpcCall) {
        try {
            return success(rpcCall.get());
        } catch (SysException failure) {
            return fromFailure(failure);
        }
    }

    /**
     * 执行需要远端确认的无返回值 RPC，并将成功结果复用为 {@link #voidSuccess()}。
     */
    public static RpcResult<Void> run(Runnable rpcCall) {
        try {
            rpcCall.run();
            return voidSuccess();
        } catch (SysException failure) {
            return fromFailure(failure);
        }
    }

    public boolean isSuccess() {
        return errorCode == 0;
    }

    public T getValue() {
        return value;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
