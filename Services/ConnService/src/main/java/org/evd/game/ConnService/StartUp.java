package org.evd.game.ConnService;

import org.evd.game.runtime.Node;
import org.evd.game.runtime.annotation.Module;
import org.evd.game.runtime.serialize.SerializerRegisterBootstrap;

@Module
public class StartUp {

    @Module.OnStart
    public static void Start(Node node){
        SerializerRegisterBootstrap.registerIfPresent(StartUp.class);
    }

    @Module.OnEnd
    public static void End(Node node){
    }
}
