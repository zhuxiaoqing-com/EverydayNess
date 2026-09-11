package org.evd.game.MatchService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.annotation.Module;
import org.evd.game.runtime.serialize.SerializerRegisterBootstrap;

/** MatchService 模块启动入口。 */
@Module
public final class StartUp {
    @Module.OnStart(priority = 0)
    public static void start(Node node) {
        SerializerRegisterBootstrap.registerIfPresent(StartUp.class);
    }

    @Module.OnEnd(priority = 1000)
    public static void end(Node node) {
    }
}
