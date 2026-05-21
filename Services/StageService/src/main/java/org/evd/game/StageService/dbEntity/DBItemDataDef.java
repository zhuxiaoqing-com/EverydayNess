package org.evd.game.StageService.dbEntity;

import com.alibaba.fastjson2.annotation.JSONField;
import org.evd.game.annotation.DBDirtyEntity;

/**
 * @author zhuxiaoqing
 * @Description: Item
 * @Date 2026/5/21 20:49
 **/
@DBDirtyEntity
public class DBItemDataDef {
    @JSONField(name = "1")
    private long itemSrl;
    @JSONField(name = "2")
    private int itemId;
    @JSONField(name = "3")
    private String itemName;

}
