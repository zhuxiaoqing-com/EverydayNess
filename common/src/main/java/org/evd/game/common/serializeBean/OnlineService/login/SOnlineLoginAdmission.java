package org.evd.game.common.serializeBean.OnlineService.login;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

/** Online 返回给 GW 的登录准入结果；queued=true 时表示请求仍在 Online 排队。 */
@SerializeClass
public class SOnlineLoginAdmission implements ISerializable {
    private SOnlineTokenState tokenState;
    private String gateAddr;
    private boolean queued;

    public SOnlineLoginAdmission() {
    }

    public SOnlineLoginAdmission(SOnlineTokenState tokenState) {
        this.tokenState = tokenState;
    }

    public static SOnlineLoginAdmission queued() {
        SOnlineLoginAdmission admission = new SOnlineLoginAdmission();
        admission.queued = true;
        return admission;
    }

    public String getGateAddr() {
        return gateAddr == null ? "" : gateAddr;
    }

    public void setGateAddr(String gateAddr) {
        this.gateAddr = gateAddr;
    }

    public SOnlineTokenState getTokenState() {
        return tokenState;
    }

    public void setTokenState(SOnlineTokenState tokenState) {
        this.tokenState = tokenState;
    }

    public boolean isQueued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }
}
