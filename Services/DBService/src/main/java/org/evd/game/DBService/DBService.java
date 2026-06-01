package org.evd.game.DBService;

import org.evd.game.DBService.storage.mysql.LoggerMysql;
import org.evd.game.DBService.storage.mysql.StorageEngine;
import org.evd.game.DBService.storage.mysql.StorageMysql;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Db.serialize.DbOpType;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.common.GlobalConfig;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.InfraConfig;
import org.evd.game.runtime.config.ServiceInfo;

@Actor
public class DBService extends Service {
    StorageEngine storageEngine;

    public DBService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Override
    public void init() {
        InfraConfig infraConfig = GlobalConfig.requireInfraConfig();
        switch (infraConfig.getDb().getEngine()) {
            case "mysql" :
                LoggerMysql loggerMysql = new LoggerMysql(infraConfig.getDb().getMysql());
                storageEngine = new StorageMysql(loggerMysql, infraConfig.getDb().getStorage());
                break;
        }
    }




    @Override
    public void onClose() {
        super.onClose();
    }


    /**
     *
     * 这里怎么弄 我又想让DB屏蔽数据库层，可以换mysql 也可以换mongoDB
     * 又想弄 pb json +mysql表;
     * 感觉如果只有 pb json 好适配 但是有mysql表 是不是从根本上已经绑定了mysql了
     * 直接用mysql算了;
     * 感觉不应该由外面传入说是mysql 还是json 还是pb;应该DBService适配;
     * 或者说外面适配,DBService内部固定死了 只能弄Mysql;
     * 其实不是应该跟着配置表的吗，配置表说初始化mysql 就是Mysql，说初始化mongodb就是,mongodb,
     * 至于mysql表的格式 那就启动的时候报错，因为不支持mysql形式的表 就报错;
     */

    @Rpc
    public DBRsp dbExec(DBReq dbReq) {
        
        DBRsp dbRsp = new DBRsp();
        dbRsp.setSuccess(true);
        try {
            switch (dbReq.getDbOpType()) {
                case DbOpType.GET:
                    dbRsp = storageEngine.find(dbReq);
                    break;
                case DbOpType.BATCH_GET:
                    dbRsp = storageEngine.findBatch(dbReq);
                    break;
                case DbOpType.SAVE:
                    storageEngine.replace(dbReq);
                    break;
                case DbOpType.BATCH_SAVE:
                    storageEngine.replaceBatch(dbReq);
                    break;
                case DbOpType.REMOVE:
                    storageEngine.remove(dbReq);
                    break;
                case DbOpType.BATCH_REMOVE:
                    storageEngine.removeBatch(dbReq);
                    break;
            }
        } catch (Exception e) {
            return new DBRsp(e.getMessage());
        }
        return dbRsp;
    }

}
