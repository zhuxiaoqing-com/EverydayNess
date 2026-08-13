package org.evd.game.runtime.util.id;

/**
 * @author zhuxiaoqing
 * @Description: IDEnum
 * @Date 2026/8/13 15:15
 **/
public enum IDEnum {

    PLAYER(1),
        ;

    private int id;

    private IDEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
