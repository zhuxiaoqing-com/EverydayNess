package org.evd.game.runtime.actor;

import lombok.extern.slf4j.Slf4j;
import org.evd.game.annotation.ServiceName;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.mailbox.MailBoxBean;
import org.evd.game.runtime.mailbox.MailBoxType;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;
import org.evd.game.runtime.support.RpcCallException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ActorRegistry {
    public static final class Registration {
        private final Object actor;
        private final ActorExecutionMode executionMode;
        private final MailBoxBean mailBoxBean;

        private Registration(
                Object actor,
                ActorExecutionMode executionMode,
                MailBoxBean mailBoxComponent
        ) {
            this.actor = actor;
            this.executionMode = executionMode;
            this.mailBoxBean = mailBoxComponent;
        }

        public Object getActor() {
            return actor;
        }

        public ActorExecutionMode getExecutionMode() {
            return executionMode;
        }

        public MailBoxBean getMailBoxBean() {
            return mailBoxBean;
        }
    }


    private final Map<ActorId, Registration> actors = new HashMap<>();
    private long nextMailBoxEpoch = 1L;

    private final LocationInterface locationInterface;
    private final Service service;

    public ActorRegistry(Service service) {
        locationInterface = (LocationInterface) ServiceName.getRpcProxyObj(ServiceName.LOCATION_SERVICE);
        this.service = service;
    }

    public void register(ActorId actorId, Object actor, ActorExecutionMode executionMode) {
        ActorId key = new ActorId(actorId);
        MailBoxBean mailBoxBean = new MailBoxBean(
                key,
                nextMailBoxEpoch++,
                executionMode == ActorExecutionMode.ORDERED ? MailBoxType.ORDERED : MailBoxType.UNORDERED);
        actors.put(key, new Registration(actor, executionMode, mailBoxBean));

        CallPoint callPoint = service.copyCallPoint();
        ActorAddress actorAddress = new ActorAddress(callPoint, mailBoxBean.getEpoch());
        locationInterface.add(callPoint, actorId, actorAddress);
    }

    public void unregister(ActorId actorId) {
        Registration remove = actors.remove(actorId);
        if(remove == null) {
            log.error("ActorRegistry unregister is null {} ", actorId);
            return;
        }
        CallPoint callPoint = service.copyCallPoint();
        ActorAddress actorAddress = new ActorAddress(callPoint, remove.getMailBoxBean().getEpoch());
        locationInterface.add(callPoint, actorId, actorAddress);
    }

    public boolean contains(ActorId actorId) {
        return actors.containsKey(actorId);
    }

    public Object get(ActorId actorId) {
        Registration registration = actors.get(actorId);
        return registration == null ? null : registration.getActor();
    }

    public Registration requireRegistration(ActorId actorId) {
        Registration registration = actors.get(actorId);
        if (registration == null) {
            throw RpcCallException.actorNotFound(actorId);
        }
        return registration;
    }

    public MailBoxBean getMailBox(ActorId actorId) {
        Registration registration = actors.get(actorId);
        return registration == null ? null : registration.getMailBoxBean();
    }

    public boolean hasSameMailBoxEpoch(ActorId actorId, long mailBoxEpoch) {
        MailBoxBean mailBoxComponent = getMailBox(actorId);
        return mailBoxComponent != null && mailBoxComponent.getEpoch() == mailBoxEpoch;
    }

    public <T> T require(ActorId actorId, Class<T> type) {
        Object actor = requireRegistration(actorId).getActor();
        if (!type.isInstance(actor)) {
            throw RpcCallException.actorTypeMismatch(actorId, type, actor.getClass());
        }
        return type.cast(actor);
    }
}
