package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;

/**
 * 可执行 RPC 请求的公共负载。
 *
 * <p>ActorMessage 与 Call 都携带这一组调用参数，但它们的路由语义不同：
 * 前者必须先进入 actor 邮箱，后者直接进入 Service RPC 分发。</p>
 */
@SerializeClass
public abstract class RpcCallBase extends CallBase {
    /** 负载分发类型，见 DispatchType。 */
    public int dispatchType = DispatchType.RPC;
    /** 目标服务调用的方法。 */
    public int methodKey;
    /** 目标服务调用方法的参数。 */
    public Object[] methodParam;
    /** 目标服务调用的方法是否需要返回值。 */
    public boolean needResult;

    public CallResult createReturn() {
        CallResult callResult = new CallResult();
        callResult.from = new CallPoint(this.to);
        callResult.to = new CallPoint(this.from);
        callResult.id = this.id;
        callResult.methodKey = this.methodKey;
        return callResult;
    }

    public int getMethodKey() {
        return methodKey;
    }

    public int getDispatchType() {
        return dispatchType;
    }

    public void setDispatchType(int dispatchType) {
        this.dispatchType = dispatchType;
    }

    public void setMethodKey(int methodKey) {
        this.methodKey = methodKey;
    }

    public Object[] getMethodParam() {
        return methodParam;
    }

    public void setMethodParam(Object[] methodParam) {
        this.methodParam = methodParam;
    }

    public boolean isNeedResult() {
        return needResult;
    }

    public void setNeedResult(boolean needResult) {
        this.needResult = needResult;
    }
}
