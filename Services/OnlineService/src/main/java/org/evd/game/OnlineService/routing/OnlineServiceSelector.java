package org.evd.game.OnlineService.routing;

import org.evd.game.OnlineService.OnlineService;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlineConnCandidate;
import org.evd.game.common.serializeBean.OnlineService.routing.OnlinePlayerCandidate;

/** OnlineService 统一负责的可负载服务选择。 */
public final class OnlineServiceSelector {
    private final OnlineLoadManager loadManager;

    /** 创建统一的服务负载选择入口。 */
    public OnlineServiceSelector(OnlineService owner) {
        this.loadManager = new OnlineLoadManager(owner);
    }

    /** 选择负载最低的 ConnService。 */
    public OnlineConnCandidate selectLeastLoadedConn() {
        return loadManager.selectLeastLoadedConn();
    }

    /** 选择负载最低的 PlayerService。 */
    public OnlinePlayerCandidate selectLeastLoadedPlayer() {
        return loadManager.selectLeastLoadedPlayer();
    }

    /** 优先返回用户历史使用过且当前仍可用的 PlayerService，否则再按负载选择。 */
    public OnlinePlayerCandidate selectLeastLoadedPlayer(String userId) {
        return loadManager.selectLeastLoadedPlayer(userId);
    }

    /** 刷新 ConnService 和 PlayerService 的负载快照。 */
    public void refresh() {
        loadManager.refresh();
    }
}
