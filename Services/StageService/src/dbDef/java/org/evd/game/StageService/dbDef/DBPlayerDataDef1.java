package org.evd.game.StageService.dbDef;

import org.evd.game.annotation.DBDirtyEntity;
import org.evd.game.annotation.DBDirtyTag;
import org.evd.game.annotation.DBserialize;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author zhuxiaoqing
 * @Description: DBPlayer
 * @Date 2026/5/21 20:48
 **/
@DBDirtyEntity(value = DBserialize.PB, table = true)
public class DBPlayerDataDef1 {
    /**
     * 字段是自己写
     */
    @DBDirtyTag(value = 1, primaryKey = true)
    private long id;
    @DBDirtyTag(2)
    private String name;
    @DBDirtyTag(3)
    private int lv;
    @DBDirtyTag(4)
    private Map<Integer, Integer> intIntMap;
    @DBDirtyTag(5)
    private List<Integer> intList;
    @DBDirtyTag(6)
    private Set<Integer> intSet;
    @DBDirtyTag(7)
    private Map<Integer, DBItemDataDef> intDBItemMap;
    @DBDirtyTag(8)
    private DBItemDataDef obj1;
    @DBDirtyTag(9)
    private byte[] bytes;


    /**
     * 操作方法全部自动生成
     */


}
