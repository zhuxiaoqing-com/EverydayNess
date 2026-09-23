package org.evd.game.runtime.actor;

import org.evd.game.annotation.service.ServiceName;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.mailbox.MailBoxBean;
import org.evd.game.runtime.rpcProxyInterface.LocationInterface;
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
import org.evd.game.runtime.support.exception.RpcCallException;
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
        if (actors.containsKey(key)) {
            throw new IllegalStateException("ActorMailBoxRegistry actor already exists: " + actorId);
        }
        MailBoxBean mailBoxBean = new MailBoxBean(
                key,
                nextMailBoxEpoch++,
                boxType == MailBoxType.ORDERED ? org.evd.game.runtime.mailbox.MailBoxType.ORDERED : org.evd.game.runtime.mailbox.MailBoxType.UNORDERED);
        actors.put(key, mailBoxBean);

        ActorAddress actorAddress = new ActorAddress(service.getCallPoint(), mailBoxBean.getEpoch());
        RpcResult<Void> result = RpcResult.run(
                () -> locationInterface.add(locationServiceRemote(), actorId, actorAddress));
        if (!result.isSuccess()) {
            log.error("ActorMailBoxRegistry 注册 Location 地址失败: service={}, actorId={}, actorAddress={}, errorCode={}, message={}",
                    service.getId(), actorId, actorAddress, result.getErrorCode(), result.getErrorMessage());
        }
    }

    public void unregister(ActorId actorId) {
        MailBoxBean remove = actors.remove(actorId);
        if(remove == null) {
            log.error("ActorMailBoxRegistry unregister is null {} ", actorId);
            return;
        }
        ActorAddress actorAddress = new ActorAddress(service.getCallPoint(), remove.getEpoch());
        RpcResult<Void> result = RpcResult.run(
                () -> locationInterface.remove(locationServiceRemote(), actorId, actorAddress));
        if (!result.isSuccess()) {
            log.error("ActorMailBoxRegistry 删除 Location 地址失败: service={}, actorId={}, actorAddress={}, errorCode={}, message={}",
                    service.getId(), actorId, actorAddress, result.getErrorCode(), result.getErrorMessage());
        }
    }

    private CallPoint locationServiceRemote() {
        CallPoint callPoint = service.getAnyInitDataSyncCallPointByType(ServiceType.LOC);
        if (callPoint == null) {
            throw new IllegalStateException("找不到 LocationService 服务路由: service=" + service.getId());
        }
        return callPoint;
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
