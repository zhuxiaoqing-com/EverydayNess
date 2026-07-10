package org.evd.game.runtime.continuation;

import jdk.internal.vm.ContinuationScope;

/**
 * ContinuationWrapper 所需的最小宿主能力。
 * 协程栈本身不再依赖 Service，具体运行时可以自行实现该接口。
 */
public interface ContinuationHost {
    ContinuationScope getScope();

    void hold(Task.ContinuationWrapper continuation);

    void unhold(Task.ContinuationWrapper continuation);
}
