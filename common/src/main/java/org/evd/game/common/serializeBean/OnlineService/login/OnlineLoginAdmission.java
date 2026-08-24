package org.evd.game.common.serializeBean.OnlineService.login;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;

/** Online 返回给 GW 的登录准入结果；queued=true 时表示请求仍在 Online 排队。 */
@SerializeClass
public class OnlineLoginAdmission implements ISerializable {
    private OnlineTokenState tokenState;
    private String gateAddr;
    private boolean queued;

    public OnlineLoginAdmission() {
    }

    public OnlineLoginAdmission(OnlineTokenState tokenState) {
        this.tokenState = tokenState;
    }

    public static OnlineLoginAdmission queued() {
        OnlineLoginAdmission admission = new OnlineLoginAdmission();
        admission.queued = true;
        return admission;
    }

    public String getGateAddr() {
        return gateAddr == null ? "" : gateAddr;
    }

    public void setGateAddr(String gateAddr) {
        this.gateAddr = gateAddr;
    }

    public OnlineTokenState getTokenState() {
        return tokenState;
    }

    public void setTokenState(OnlineTokenState tokenState) {
        this.tokenState = tokenState;
    }

    public boolean isQueued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }
}
