package org.evd.game.runtime.rpcProxyInterface;

import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.call.CallPoint;


public interface DBExecInterface {
   public DBRsp dbExec(CallPoint remote, DBReq dbReq);

   //public DBRsp dbExec(CallPoint remote, DBReq dbReq, long timeoutMillis);
}
