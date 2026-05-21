package org.evd.game.StageService.dbEntity;

import com.alibaba.fastjson2.annotation.JSONField;
import org.evd.game.annotation.DBDirtyEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author zhuxiaoqing
 * @Description: DBPlayer
 * @Date 2026/5/21 20:48
 **/
@DBDirtyEntity(table = true)
public class DBPlayerDataDef{
    /**
     * 字段是自己写
     */
    @JSONField(name = "1")
    private long id;
    @JSONField(name = "2")
    private String name;
    @JSONField(name = "3")

    private int lv;
    @JSONField(name = "4")
    private Map<Integer, Integer> intIntMap;
    @JSONField(name = "5")
    private List<Integer> intList;
    @JSONField(name = "6")
    private Set<Integer> intSet;
    @JSONField(name = "7")
    private Map<Integer, DBItemDataDef> intDBItemMap;


    /**
     * 操作方法全部自动生成
     */


}
