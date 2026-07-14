package org.evd.game.common.serializeBean.ConnService;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

@SerializeClass
public class ConnInfoBase implements ISerializable {
    @SerializeField
    private int con1;
    private int con2;

    public int getCon1() {
        return con1;
    }

    public void setCon1(int con1) {
        this.con1 = con1;
    }

    public int getCon2() {
        return con2;
    }

    public void setCon2(int con2) {
        this.con2 = con2;
    }
}
