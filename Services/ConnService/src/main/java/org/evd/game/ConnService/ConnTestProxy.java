package org.evd.game.ConnService;

import org.evd.game.annotation.Rpc;
import org.evd.game.common.serializeBean.ConnInfo;

/**
 * @author zhuxiaoqing
 * @Description: ConnTestProxy
 * @Date 2026/5/14 13:55
 **/
public class ConnTestProxy {
    @Rpc
    public void connTest1() {

    }


    @Rpc
    public void connTest2(int a, Object b, ConnInfo connInfo) {

    }

}
