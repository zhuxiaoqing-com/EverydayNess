package org.evd.game.runtime.Db.table;

import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.call.CallPoint;

@FunctionalInterface
public interface DBExecInterface {
   public DBRsp dbExec(CallPoint remote, DBReq dbReq);
}
