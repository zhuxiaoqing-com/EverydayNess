package org.evd.game.StageService.db.bean;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.DirtyObject;
import org.evd.game.base.ISerializable;
import org.evd.game.base.InputStreamBase;
import org.evd.game.base.OutputStreamBase;
import com.alibaba.fastjson2.annotation.JSONField;
import java.io.IOException;

@SerializeClass(customized = true)
public final class DBItemDataJSON extends DirtyObject implements ISerializable {
    @JSONField(name = "1")
    private long itemSrl;
    @JSONField(name = "2")
    private int itemId;
    @JSONField(name = "3")
    private String itemName;

    DBItemDataJSON(DirtyObject _xp_) {
        super(_xp_);
        this.itemSrl = 0L;
        this.itemId = 0;
        this.itemName = "";
    }

    public DBItemDataJSON() {
        this((DirtyObject)null);
    }

    public DBItemDataJSON(DBItemDataJSON _o_) {
        this(_o_, null);
    }

    DBItemDataJSON(DBItemDataJSON _o_, DirtyObject _xp_) {
        super(_xp_);
        this.itemSrl = _o_.itemSrl;
        this.itemId = _o_.itemId;
        this.itemName = _o_.itemName;
        this.dirty = false;
    }

    public void copyFrom(DBItemDataJSON _o_) {
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
    public void writeTo(OutputStreamBase out) throws IOException {
        out.writeLong(this.itemSrl);
        out.writeInt(this.itemId);
        out.writeString(this.itemName);
    }

    @Override
    public void readFrom(InputStreamBase in) throws IOException {
        long _v_0 = in.readLong();
        this.itemSrl = _v_0;
        int _v_1 = in.readInt();
        this.itemId = _v_1;
        String _v_2 = in.readString();
        this.itemName = _v_2;
        this.dirty = false;
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
