package org.evd.game.runtime.actor;

import org.evd.game.annotation.ServiceName;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.mailbox.MailBoxBean;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;
import org.evd.game.runtime.support.RpcCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ActorMailBoxRegistry {
    private static final Logger log = LoggerFactory.getLogger(ActorMailBoxRegistry.class);

    private final Map<ActorId, MailBoxBean> actors = new HashMap<>();
    private long nextMailBoxEpoch = 1L;

    private final LocationInterface locationInterface;
    private final Service service;

    public ActorMailBoxRegistry(Service service) {
        locationInterface = (LocationInterface) ServiceName.getRpcProxyObj(ServiceName.LOCATION_SERVICE);
        this.service = service;
    }

    public void register(ActorId actorId, MailBoxType boxType) {
        ActorId key = new ActorId(actorId);
        MailBoxBean mailBoxBean = new MailBoxBean(
                key,
                nextMailBoxEpoch++,
                boxType == MailBoxType.ORDERED ? org.evd.game.runtime.mailbox.MailBoxType.ORDERED : org.evd.game.runtime.mailbox.MailBoxType.UNORDERED);
        actors.put(key, mailBoxBean);

        CallPoint callPoint = service.copyCallPoint();
        ActorAddress actorAddress = new ActorAddress(callPoint, mailBoxBean.getEpoch());
        locationInterface.add(callPoint, actorId, actorAddress);
    }

    public void unregister(ActorId actorId) {
        MailBoxBean remove = actors.remove(actorId);
        if(remove == null) {
            log.error("ActorMailBoxRegistry unregister is null {} ", actorId);
            return;
        }
        CallPoint callPoint = service.copyCallPoint();
        ActorAddress actorAddress = new ActorAddress(callPoint, remove.getEpoch());
        locationInterface.remove(callPoint, actorId);
    }

    public boolean contains(ActorId actorId) {
        return actors.containsKey(actorId);
    }

    public MailBoxBean requireMailBox(ActorId actorId) {
        MailBoxBean mailBoxBean = actors.get(actorId);
        if (mailBoxBean == null) {
            throw RpcCallException.actorNotFound(actorId);
        }
        return mailBoxBean;
    }

    public MailBoxBean getMailBox(ActorId actorId) {
        return actors.get(actorId);
    }

    public boolean hasSameMailBoxEpoch(ActorId actorId, long mailBoxEpoch) {
        MailBoxBean mailBoxComponent = getMailBox(actorId);
        return mailBoxComponent != null && mailBoxComponent.getEpoch() == mailBoxEpoch;
    }
}
