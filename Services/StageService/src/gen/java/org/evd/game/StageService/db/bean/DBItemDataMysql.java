package org.evd.game.StageService.db.bean;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.DirtyObject;
import org.evd.game.base.ISerializable;
import org.evd.game.base.InputStreamBase;
import org.evd.game.base.OutputStreamBase;
import java.io.IOException;

@SerializeClass(customized = true)
public final class DBItemDataMysql extends DirtyObject implements ISerializable {
    private long id;
    private String name;

    DBItemDataMysql(DirtyObject _xp_) {
        super(_xp_);
        this.id = 0L;
        this.name = "";
    }

    public DBItemDataMysql() {
        this((DirtyObject)null);
    }

    public DBItemDataMysql(DBItemDataMysql _o_) {
        this(_o_, null);
    }

    DBItemDataMysql(DBItemDataMysql _o_, DirtyObject _xp_) {
        super(_xp_);
        this.id = _o_.id;
        this.name = _o_.name;
        this.dirty = false;
    }

    public void copyFrom(DBItemDataMysql _o_) {
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
