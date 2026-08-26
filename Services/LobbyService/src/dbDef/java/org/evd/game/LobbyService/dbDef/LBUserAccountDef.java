package org.evd.game.LobbyService.dbDef;

import org.evd.game.annotation.serialize.DBDirtyEntity;
import org.evd.game.annotation.serialize.DBDirtyTag;
import org.evd.game.annotation.serialize.DBserialize;

import java.util.List;

/** Lobby 用户账号持久化数据；角色详情由 LBRoleDef 持久化，登录会话由 OnlineService 管理。 */
@DBDirtyEntity(value = DBserialize.PB, table = true)
public class LBUserAccountDef {
    @DBDirtyTag(value = 1, primaryKey = true)
    private String userId;
    @DBDirtyTag(2)
    private int status;
    @DBDirtyTag(3)
    private String banReason;
    @DBDirtyTag(4)
    private long createTime;
    @DBDirtyTag(5)
    private long lastLoginTime;
    @DBDirtyTag(6)
    private List<Long> playerIds;
}
