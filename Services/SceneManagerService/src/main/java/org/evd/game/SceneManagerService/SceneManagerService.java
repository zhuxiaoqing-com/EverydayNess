package org.evd.game.SceneManagerService;

import org.evd.game.SceneManagerService.disconnect.SceneManagerServiceConnectLogic;
import org.evd.game.SceneManagerService.disconnect.SceneManagerServiceDisconnectLogic;
import org.evd.game.SceneManagerService.routing.SceneManagerRoutingLogic;
import org.evd.game.SceneManagerService.scene.SceneDealManager;
import org.evd.game.runtime.util.id.SceneIdGenerator;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.ymlconfig.RegisteredService;
import org.evd.game.runtime.ymlconfig.ServiceInfo;

import java.util.Collection;

/** SceneManagerService 持有场景 Deal 并生成场景 ID。 */
public class SceneManagerService extends Service {
    private final SceneDealManager sceneDealManager;

    @Override
    protected void onServiceDisconnect(Collection<RegisteredService> services) {
        getActor(SceneManagerServiceDisconnectLogic.class).onServiceDisconnect(services);
    }

    private static final long STAGE_MAP_COUNT_REFRESH_INTERVAL_MILLIS = 5_000L;

    public SceneManagerService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
        sceneDealManager = new SceneDealManager(this);
    }

    @Override
    public void init() {
        super.init();
        newRepeatedTimerCoroutine(STAGE_MAP_COUNT_REFRESH_INTERVAL_MILLIS, true,
                () -> getActor(SceneManagerRoutingLogic.class).refreshStageMapCounts());
    }

    public SceneDealManager sceneDealManager() {
        return sceneDealManager;
    }

    public long createSceneId() {
        return SceneIdGenerator.nextId();
    }

    public CallPoint getSceneStage(long sceneId) {
        return sceneDealManager.getSceneStage(sceneId);
    }

}
