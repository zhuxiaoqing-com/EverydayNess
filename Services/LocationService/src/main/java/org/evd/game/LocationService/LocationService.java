package org.evd.game.LocationService;

import org.evd.game.annotation.Actor;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

@Actor(single = true)
public class LocationService extends Service {
    private final Map<Long, CallPoint> actorLocations = new HashMap<>();

    public LocationService(Node node, String name, String scheduledName) {
        super(node, name, scheduledName);
    }

    public LocationService(Node node, String name, String scheduledName, int interval) {
        super(node, name, scheduledName, interval);
    }

    public void bindActor(long actorId, CallPoint callPoint) {
        actorLocations.put(actorId, new CallPoint(callPoint));
        LogCore.core.info("LocationService 绑定actor: actorId={}, node={}, service={}",
                actorId, callPoint.getNodeId(), callPoint.getServId());
    }

    public void unbindActor(long actorId, CallPoint callPoint) {
        CallPoint current = actorLocations.get(actorId);
        if (current == null) {
            return;
        }
        if (!samePoint(current, callPoint)) {
            return;
        }
        actorLocations.remove(actorId);
        LogCore.core.info("LocationService 移除actor: actorId={}, node={}, service={}",
                actorId, callPoint.getNodeId(), callPoint.getServId());
    }

    public CallPoint getActor(long actorId) {
        CallPoint callPoint = actorLocations.get(actorId);
        return callPoint == null ? null : new CallPoint(callPoint);
    }

    private boolean samePoint(CallPoint left, CallPoint right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getNodeId().equals(right.getNodeId())
                && left.getServId().equals(right.getServId());
    }
}
