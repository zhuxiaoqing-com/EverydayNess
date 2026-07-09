package org.evd.game.runtime.call;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;

@SerializeClass
public class CallPing extends CallBase {
    @SerializeField
    public String addr;

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    @Override
    public String toString() {
        return "CallPing{" +
                "id=" + id +
                ", to=" + to +
                ", from=" + from +
                ", addr='" + addr + '\'' +
                '}';
    }
}
