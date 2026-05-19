package org.evd.game.LocationService;

import org.evd.game.annotation.Actor;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.mailbox.MailboxKey;
import org.evd.game.runtime.support.LogCore;

import java.util.HashMap;
import java.util.Map;

@Actor(single = true)
public class LocationService extends Service {
    private final Map<MailboxKey, CallPoint> mailboxLocations = new HashMap<>();

    public LocationService(Node node, String name, String scheduledName) {
        super(node, name, scheduledName);
    }

    public LocationService(Node node, String name, String scheduledName, int interval) {
        super(node, name, scheduledName, interval);
    }

    public void bindMailbox(MailboxKey mailboxKey, CallPoint callPoint) {
        mailboxLocations.put(new MailboxKey(mailboxKey), new CallPoint(callPoint));
        LogCore.core.info("LocationService 绑定mailbox: mailboxKey={}, node={}, service={}",
                mailboxKey, callPoint.getNodeId(), callPoint.getServId());
    }

    public void unbindMailbox(MailboxKey mailboxKey, CallPoint callPoint) {
        CallPoint current = mailboxLocations.get(mailboxKey);
        if (current == null) {
            return;
        }
        if (!samePoint(current, callPoint)) {
            return;
        }
        mailboxLocations.remove(mailboxKey);
        LogCore.core.info("LocationService 移除mailbox: mailboxKey={}, node={}, service={}",
                mailboxKey, callPoint.getNodeId(), callPoint.getServId());
    }

    public CallPoint getMailbox(MailboxKey mailboxKey) {
        CallPoint callPoint = mailboxLocations.get(mailboxKey);
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
