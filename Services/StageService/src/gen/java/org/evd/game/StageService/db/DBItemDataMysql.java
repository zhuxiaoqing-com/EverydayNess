package org.evd.game.StageService.db;
import org.evd.game.base.DirtyObject;

public final class DBItemDataMysql extends DirtyObject {
    private long itemSrl;
    private int itemId;
    private String itemName;

    DBItemDataMysql(DirtyObject _xp_) {
        super(_xp_);
        this.itemName = "";
    }

    public DBItemDataMysql() {
        this((DirtyObject)null);
    }

    public DBItemDataMysql(DBItemDataMysql _o_) {
        this(_o_, null);
    }

    DBItemDataMysql(DBItemDataMysql _o_, DirtyObject _xp_) {
        super(_xp_);
        this.itemSrl = _o_.itemSrl;
        this.itemId = _o_.itemId;
        this.itemName = _o_.itemName;
        this.dirty = false;
    }

    public void copyFrom(DBItemDataMysql _o_) {
        this.itemSrl = _o_.itemSrl;
        this.itemId = _o_.itemId;
        this.itemName = _o_.itemName;
        makeModify();
    }

    public long getItemSrl(){
        return this.itemSrl;
    }

    public void setItemSrl(long _v_){
        this.itemSrl = _v_;
        makeModify();
    }

    public int getItemId(){
        return this.itemId;
    }

    public void setItemId(int _v_){
        this.itemId = _v_;
        makeModify();
    }

    public String getItemName(){
        return this.itemName;
    }

    public void setItemName(String _v_){
        this.itemName = _v_;
        makeModify();
    }

    @Override
    public String toString() {
        StringBuilder _sb_ = new StringBuilder(super.toString());
        _sb_.append("=(");
        _sb_.append("itemSrl=").append(itemSrl).append(",");
        _sb_.append("itemId=").append(itemId).append(",");
        _sb_.append("itemName=").append(itemName == null ? "null" : "T" + itemName.length()).append(",");
        _sb_.append(")");
        return _sb_.toString();
    }
}
