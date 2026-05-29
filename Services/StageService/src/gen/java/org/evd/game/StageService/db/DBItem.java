package org.evd.game.StageService.db;
import io.protostuff.Tag;
import org.evd.game.base.DirtyObject;

public final class DBItem extends DirtyObject {
    @Tag(1)
    private long itemSrl;
    @Tag(2)
    private int itemId;
    @Tag(3)
    private String itemName;

    DBItem(DirtyObject _xp_) {
        super(_xp_);
        this.itemName = "";
    }

    public DBItem() {
        this((DirtyObject)null);
    }

    public DBItem(DBItem _o_) {
        this(_o_, null);
    }

    DBItem(DBItem _o_, DirtyObject _xp_) {
        super(_xp_);
        this.itemSrl = _o_.itemSrl;
        this.itemId = _o_.itemId;
        this.itemName = _o_.itemName;
        this.dirty = false;
    }

    public void copyFrom(DBItem _o_) {
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
