package org.evd.game.StageService.db.bean;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.DirtyObject;
import org.evd.game.base.ISerializable;
import org.evd.game.base.InputStreamBase;
import org.evd.game.base.OutputStreamBase;
import io.protostuff.Tag;
import java.io.IOException;

@SerializeClass(customized = true)
public final class DBItemData extends DirtyObject implements ISerializable {
    @Tag(1)
    private long id;
    @Tag(2)
    private String name;

    DBItemData(DirtyObject _xp_) {
        super(_xp_);
        this.id = 0L;
        this.name = "";
    }

    public DBItemData() {
        this((DirtyObject)null);
    }

    public DBItemData(DBItemData _o_) {
        this(_o_, null);
    }

    DBItemData(DBItemData _o_, DirtyObject _xp_) {
        super(_xp_);
        this.id = _o_.id;
        this.name = _o_.name;
        this.dirty = false;
    }

    public void copyFrom(DBItemData _o_) {
        this.id = _o_.id;
        this.name = _o_.name;
        makeModify();
    }

    public long getId(){
        return this.id;
    }

    public void setId(long _v_){
        this.id = _v_;
        makeModify();
    }

    public String getName(){
        return this.name;
    }

    public void setName(String _v_){
        this.name = _v_;
        makeModify();
    }

    @Override
    public void writeTo(OutputStreamBase out) throws IOException {
        out.writeLong(this.id);
        out.writeString(this.name);
    }

    @Override
    public void readFrom(InputStreamBase in) throws IOException {
        long _v_0 = in.readLong();
        this.id = _v_0;
        String _v_1 = in.readString();
        this.name = _v_1;
        this.dirty = false;
    }

    @Override
    public String toString() {
        StringBuilder _sb_ = new StringBuilder(super.toString());
        _sb_.append("=(");
        _sb_.append("id=").append(id).append(",");
        _sb_.append("name=").append(name == null ? "null" : "T" + name.length()).append(",");
        _sb_.append(")");
        return _sb_.toString();
    }
}
