package org.evd.game.PlayerService.map;

import org.evd.game.PlayerService.dbDef.db.bean.DBPlayerData;
import org.evd.game.PlayerService.dbDef.db.table.DBPlayerDataTable;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapData;
import org.evd.game.common.serializeBean.SceneManagerService.routing.SPlayerMapSimpleData;

/** 负责构建地图流程使用的玩家数据。 */
@Actor
public final class PlayerMapDataLogic {
    public SPlayerMapSimpleData getSimpleData(long playerId) {
        return new SPlayerMapSimpleData(playerId);
    }

    public SPlayerMapData getData(long playerId) {
        DBPlayerData data = DBPlayerDataTable.get(playerId);
        if (data == null) {
            return new SPlayerMapData(playerId, "", 0);
        }
        return new SPlayerMapData(data.getId(), data.getName(), data.getLv());
    }
}
