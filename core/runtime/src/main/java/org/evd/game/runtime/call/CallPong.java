package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;

@SerializeClass
public class CallPong extends CallBase {

    @Override
    public String toString() {
        return "CallPong{" +
                "id=" + id +
                ", to=" + to +
                ", from=" + from +
                '}';
    }
}
