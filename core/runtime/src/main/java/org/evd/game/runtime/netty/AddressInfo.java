package org.evd.game.runtime.netty;

/**
 * @author zhuxiaoqing
 * @Description: AddressInfo
 * @Date 2026/7/3 20:12
 **/
public class AddressInfo {
    String host;
    int port;

    public AddressInfo(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * @param addr 例子： 0.0.0.0:18080
     */
    public AddressInfo(String addr) {
        int colon = addr.lastIndexOf(':');
        if (colon <= 0 || colon == addr.length() - 1) {
            throw new IllegalArgumentException("invalid address: " + addr);
        }

        this.host = addr.substring(0, colon);
        this.port = Integer.parseInt(addr.substring(colon + 1));
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
