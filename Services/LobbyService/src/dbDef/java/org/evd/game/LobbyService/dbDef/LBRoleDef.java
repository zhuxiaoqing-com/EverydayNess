package org.evd.game.LobbyService.dbDef;

import org.evd.game.annotation.serialize.DBDirtyEntity;
import org.evd.game.annotation.serialize.DBDirtyTag;
import org.evd.game.annotation.serialize.DBserialize;

/** Lobby 角色持久化数据；登录会话和角色运行时状态由其他服务管理。 */
@DBDirtyEntity(value = DBserialize.PB, table = true)
public class LBRoleDef {
    @DBDirtyTag(value = 1, primaryKey = true)
    private long playerId;
    @DBDirtyTag(2)
    private String userId;
    @DBDirtyTag(3)
    private String name;
    @DBDirtyTag(4)
    private int level;
    @DBDirtyTag(5)
    private int characterId;
}
