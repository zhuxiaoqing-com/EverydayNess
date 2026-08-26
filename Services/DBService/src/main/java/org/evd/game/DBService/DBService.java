package org.evd.game.DBService;

import org.evd.game.annotation.RpcService;
import org.evd.game.runtime.Db.serialize.DBReq;
import org.evd.game.runtime.Db.serialize.DBRsp;
import org.evd.game.runtime.Node;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.config.ServiceInfo;
import org.evd.game.runtime.rpcProxyInterface.DBExecInterface;
import org.evd.game.runtime.support.exception.ServiceStoppingException;

@RpcService(DBExecInterface.class)
public class DBService extends Service {
    private DBProxy dbProxy;


    public DBService(Node node, String name, String scheduledName, int interval, ServiceInfo serviceInfo) {
        super(node, name, scheduledName, interval, serviceInfo);
    }

    @Override
    public void init() {
        super.init();
        dbProxy = new DBProxy();
    }

    @Override
    protected final void onStop(boolean force) {
        super.onStop(force);
        if (dbProxy != null) {
            dbProxy.stop(force);
            dbProxy = null;
        }
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
     *
     * 这里mysql必须传sql,因为mysql配置表形式需要知道配置表名字，
     */

    public DBRsp dbExec(DBReq dbReq) {
        if (isStopping()) {
            throw new ServiceStoppingException("dbExec service is stopping");
        }
        return dbProxy.dbExec(null, dbReq);
    }


    @Override
    protected boolean supportLocation() {
        return false;
    }


}
