package org.evd.game.runtime.Db.table;

import org.evd.game.base.DirtyObject;

/** 接收 APT 注册的单张 DB 表；Service 启动和离线恢复使用各自的实现。 */
public interface DbTableRegistrar {
    void registerTable(Class<?> tableFacadeClass,
                       Class<? extends DirtyObject> beanClass,
                       Class<? extends TTable<?, ?>> tableImplClass,
                       String tableName,
                       Object keySample);
}
