package org.evd.game.DbEntity.serialize;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhuxiaoqing
 * @Description: MysqlData
 * @Date 2026/5/26 20:38
 **/
@SerializeClass
public class MysqlRsp implements ISerializable {
    @SerializeField
    private List<DbTableField> tablFieldList = new ArrayList<>();

    public List<DbTableField> getTablFieldList() {
        return tablFieldList;
    }

    public void setTablFieldList(List<DbTableField> tablFieldList) {
        this.tablFieldList = tablFieldList;
    }
}
