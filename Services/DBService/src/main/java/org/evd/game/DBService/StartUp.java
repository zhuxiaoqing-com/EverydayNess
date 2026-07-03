package org.evd.game.DBService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.annotation.Module;
import org.evd.game.runtime.serialize.SerializerRegisterBootstrap;

@Module
public class StartUp {

    @Module.OnStart(priority = 0)
    public static void start(Node node) {
        SerializerRegisterBootstrap.registerIfPresent(StartUp.class);
    }

    @Module.OnEnd(priority = 1000)
    public static void end(Node node) {
    }
}
