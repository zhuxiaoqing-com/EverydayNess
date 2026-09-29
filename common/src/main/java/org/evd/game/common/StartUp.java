package org.evd.game.common;

import org.evd.game.common.constant.MapConst;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.annotation.Module;
import org.evd.game.runtime.serialize.InputStream;

@Module
public class StartUp {

    @Module.OnStart(priority = 0)
    public static void Start(Node node){
        SerializerRegister.register();
        ProtoMessageRegistry.register();
        // RPC 返回类型不会进入 ISerializable 字段注册，需要显式注册枚举反序列化。
        InputStream.registerSerializeReadEnumFunc(MapConst.SceneEnterResult.class.getName().hashCode(),
                MapConst.SceneEnterResult.class,
                (input, ordinal) -> MapConst.SceneEnterResult.values()[ordinal]);
    }
    @Module.OnEnd(priority = 1000)
    public static void End(Node node){
    }
}
