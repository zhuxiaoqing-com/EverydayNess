package org.evd.game.runtime.config;

/** 数据库部署形态。 */
public enum DbTopology {
    /** 通过独立 DBService 的 RPC 访问数据库。 */
    REMOTE_SERVICE,
    /** 每个 Node 持有一个本地数据库入口。 */
    NODE_LOCAL
}
