package org.evd.game.SceneManagerService.scene;

/** SceneBattle 的生命周期状态。 */
public enum SMSceneState {
    CREATING,
    CREATED,
    /** Stage 断链期间保留路由数据，但禁止新的进入请求。 */
    UNAVAILABLE,
    DESTROYED
}
