package org.evd.game.PlayerService.player;

import org.evd.game.PlayerService.dbDef.db.bean.DBPlayerData;
import org.evd.game.PlayerService.dbDef.db.table.DBPlayerDataTable;
import org.evd.game.runtime.support.LogCore;

/** Player 基础数据仓库，只负责访问 PlayerService 的 MDB 表。 */
public final class PlayerDataRepository {
    /** 加载已有 Player；数据库没有记录时创建最小基础数据并立即写入。 */
    public void loadOrCreate(long playerId, String name, int level) {
        DBPlayerData existing = DBPlayerDataTable.get(playerId);
        if (existing != null) {
            return;
        }

        DBPlayerData created = new DBPlayerData();
        created.setId(playerId);
        created.setName(name == null ? "" : name);
        created.setLv(level);
        DBPlayerDataTable.add(playerId, created, true);
        LogCore.core.info("PlayerService 创建玩家基础数据: playerId={}, name={}, level={}",
                playerId, created.getName(), created.getLv());
    }
}
