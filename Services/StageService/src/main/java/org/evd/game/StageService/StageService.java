package org.evd.game.StageService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.actor.MailBoxType;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

import java.util.HashMap;
import java.util.Map;

public class StageService extends Service {
    private final Map<Long, StageMap> maps = new HashMap<>();

    public StageService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    /** 创建地图实例；相同实例重复创建时保持幂等。 */
    public boolean createMap(long mapInstanceId, int mapConfigId) {
        if (mapInstanceId <= 0L || mapConfigId <= 0) {
            return false;
        }
        StageMap current = maps.get(mapInstanceId);
        if (current != null) {
            return current.mapConfigId() == mapConfigId;
        }
        registerActor(ActorId.map(mapInstanceId), MailBoxType.ORDERED);
        maps.put(mapInstanceId, new StageMap(mapConfigId));
        return true;
    }

    /** 销毁没有玩家的地图实例。 */
    public boolean destroyMap(long mapInstanceId) {
        StageMap map = maps.get(mapInstanceId);
        if (map == null) {
            return true;
        }
        if (!map.isEmpty()) {
            return false;
        }
        maps.remove(mapInstanceId);
        unregisterActor(ActorId.map(mapInstanceId));
        return true;
    }

    public boolean enterMap(long mapInstanceId, long playerId, long enterSeq) {
        StageMap map = maps.get(mapInstanceId);
        return map != null && map.enter(playerId, enterSeq);
    }

    public boolean leaveMap(long mapInstanceId, long playerId, long enterSeq) {
        StageMap map = maps.get(mapInstanceId);
        return map == null || map.leave(playerId, enterSeq);
    }

    private static final class StageMap {
        private final int mapConfigId;
        private final Map<Long, Long> players = new HashMap<>();

        private StageMap(int mapConfigId) {
            this.mapConfigId = mapConfigId;
        }

        private int mapConfigId() {
            return mapConfigId;
        }

        private boolean isEmpty() {
            return players.isEmpty();
        }

        private boolean enter(long playerId, long enterSeq) {
            if (playerId <= 0L || enterSeq <= 0L) {
                return false;
            }
            Long currentSeq = players.get(playerId);
            if (currentSeq != null) {
                return currentSeq == enterSeq;
            }
            players.put(playerId, enterSeq);
            return true;
        }

        private boolean leave(long playerId, long enterSeq) {
            Long currentSeq = players.get(playerId);
            if (currentSeq == null) {
                return true;
            }
            if (currentSeq != enterSeq) {
                return false;
            }
            players.remove(playerId);
            return true;
        }
    }
}
