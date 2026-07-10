package org.evd.game.runtime.continuation;

import java.util.ArrayList;
import java.util.List;

public class ContinuationPool {
    private final ContinuationHost host;
    private final List<Task.ContinuationWrapper> pool = new ArrayList<>();
    public ContinuationPool(ContinuationHost host){
        this.host = host;
    }

    public Task.ContinuationWrapper apply(){
        if (pool.isEmpty())
            return new Task.ContinuationWrapper(host);
        else
            return pool.removeLast();
    }

    public void recycle(Task.ContinuationWrapper callBack) {
        // 清理
        callBack.close();
        // 回收
        pool.add(callBack);
    }

    public void clear() {
        pool.clear();
    }
}
