package org.evd.game.runtime;

import org.evd.game.annotation.actor.ActorType;
import org.evd.game.annotation.serialize.DBserialize;
import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.annotation.Module;
import org.evd.game.runtime.serialize.InputStream;

@Module
public class StartUp {

    @Module.OnStart(priority = 0)
    public static void Start(Node node){
        SerializerRegister.register();
        registerSharedEnums();
    }
    @Module.OnEnd(priority = 1000)
    public static void End(Node node){
    }

    private static void registerSharedEnums() {
        // 这些枚举定义在 annotation 模块，不会生成本包 SerializerRegister，
        // 但会出现在跨节点传输对象里，所以需要在 runtime 启动时显式补注册。
        InputStream.registerSerializeReadEnumFunc(ServiceType.class.getName().hashCode(),
                ServiceType.class, StartUp::readServiceType);
        InputStream.registerSerializeReadEnumFunc(ActorType.class.getName().hashCode(),
                ActorType.class, StartUp::readActorType);
        InputStream.registerSerializeReadEnumFunc(DBserialize.class.getName().hashCode(),
                DBserialize.class, StartUp::readDbSerialize);
    }

    private static Enum<?> readServiceType(InputStream in, int ordinal) {
        return ServiceType.values()[ordinal];
    }

    private static Enum<?> readActorType(InputStream in, int ordinal) {
        return ActorType.values()[ordinal];
    }

    private static Enum<?> readDbSerialize(InputStream in, int ordinal) {
        return DBserialize.values()[ordinal];
    }
}
