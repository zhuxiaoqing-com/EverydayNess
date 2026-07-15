package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;

import java.util.Arrays;

@SerializeClass
public class Call extends RpcCallBase {

    public Call() {
    }

    @Override
    public String toString() {
        return "Call{" +
                "id=" + id +
                ", to=" + to +
                ", from=" + from +
                ", needResult=" + needResult +
                ", methodParam=" + Arrays.toString(methodParam) +
                ", methodKey=" + methodKey +
                ", dispatchType=" + dispatchType +
                '}';
    }
}
