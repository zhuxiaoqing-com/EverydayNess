package org.evd.game.StageService;

import org.evd.game.runtime.RPCImplBase;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.function.*;

/**
* 根据StageServiceService生成的rpc分发类
*/
public class StageServiceImpl extends RPCImplBase {
    public final static class EnumCall{
        public final static int ENUM_STAGESERVICE_STRING_DOSOME1_INT_INT = 0;
        public final static int ENUM_STAGESERVICE_VOID_DOSOME2_INT_INT = 1;
        public final static int ENUM_STAGESERVICE_STRING_DOSOME3_INT = 2;
        public final static int ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT = 3;
        public final static int ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT = 4;
    }
    private HaHaHaActor haHaHaActor;

    private HaHaHaActor haHaHaActor() {
        if (haHaHaActor == null) {
            haHaHaActor = new HaHaHaActor();
        }
        return haHaHaActor;
    }

    @Override
    public Object getMethodFunction(Service serv, int methodKey) {
        StageService service = (StageService) serv;
        switch (methodKey){
            case EnumCall.ENUM_STAGESERVICE_STRING_DOSOME1_INT_INT:
                return (ReturnFunction2<String, Integer, Integer>)service::doSome1;
            case EnumCall.ENUM_STAGESERVICE_VOID_DOSOME2_INT_INT:
                return (Function2<Integer, Integer>)service::doSome2;
            case EnumCall.ENUM_STAGESERVICE_STRING_DOSOME3_INT:
                return (ReturnFunction1<String, Integer>)service::doSome3;
            case EnumCall.ENUM_HAHAHAACTOR_VOID_RPC1_INT_INT:
                return (Function2<Integer, Integer>)haHaHaActor()::rpc1;
            case EnumCall.ENUM_HAHAHAACTOR_VOID_RPC2_OBJECT_OBJECT:
                return (Function2<Object, Object>)haHaHaActor()::rpc2;
            default:
                return null;
        }
    }
}
