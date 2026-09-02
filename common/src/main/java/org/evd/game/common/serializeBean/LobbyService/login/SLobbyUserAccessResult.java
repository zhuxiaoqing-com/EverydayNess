package org.evd.game.common.serializeBean.LobbyService.login;

import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;

/** Lobby 对用户登录资格的校验结果。 */
@SerializeClass
public class SLobbyUserAccessResult implements ISerializable {
    private boolean allowed;
    private boolean created;
    private String message;

    public SLobbyUserAccessResult() {
    }

    private SLobbyUserAccessResult(boolean allowed, boolean created, String message) {
        this.allowed = allowed;
        this.created = created;
        this.message = message;
    }

    public static SLobbyUserAccessResult allowed(boolean created) {
        return new SLobbyUserAccessResult(true, created, "ok");
    }

    public static SLobbyUserAccessResult denied(String message) {
        return new SLobbyUserAccessResult(false, false, message);
    }

    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public boolean isCreated() { return created; }
    public void setCreated(boolean created) { this.created = created; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
