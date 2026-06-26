package org.evd.game.SceneManagerService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.annotation.Module;

@Module
public class StartUp {

    @Module.OnStart(priority = 0)
    public static void start(Node node) {
    }

    @Module.OnEnd(priority = 1000)
    public static void end(Node node) {
    }
}
