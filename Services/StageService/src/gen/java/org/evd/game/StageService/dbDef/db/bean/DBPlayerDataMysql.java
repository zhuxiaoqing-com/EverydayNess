package org.evd.game.StageService.dbDef.db.bean;

import org.evd.game.base.DirtyObject;
import org.evd.game.base.ISerializable;
import org.evd.game.base.InputStreamBase;
import org.evd.game.base.OutputStreamBase;
import org.evd.game.runtime.Db.collection.XArrayList;
import org.evd.game.runtime.Db.collection.XHashMap;
import org.evd.game.runtime.Db.collection.XHashSet;
import java.io.IOException;

public final class DBPlayerDataMysql extends DirtyObject implements ISerializable {
    private int id;
    private String name;
    private int lv;
    private XHashMap<Integer, Integer> intIntMap;
    private XArrayList<Integer> intList;
    private XHashSet<Integer> intSet;
    private XHashMap<Integer, DBItemDataMysql> intDBItemMap;
    private DBItemDataMysql obj1;
    private byte[] bytes;

    DBPlayerDataMysql(DirtyObject _xp_) {
        super(_xp_);
        this.id = 0;
        this.name = "";
        this.lv = 0;
        this.intIntMap = new XHashMap<>(this);
        this.intList = new XArrayList<>(this);
        this.intSet = new XHashSet<>(this);
        this.intDBItemMap = new XHashMap<>(this);
    }

    public DBPlayerDataMysql() {
        this((DirtyObject)null);
    }

    public DBPlayerDataMysql(DBPlayerDataMysql _o_) {
        this(_o_, null);
    }

    DBPlayerDataMysql(DBPlayerDataMysql _o_, DirtyObject _xp_) {
        super(_xp_);
        this.id = _o_.id;
        this.name = _o_.name;
        this.lv = _o_.lv;
        this.intIntMap = new XHashMap<>(this);
        this.intIntMap.putAll(_o_.intIntMap);
        this.intList = new XArrayList<>(this);
        this.intList.addAll(_o_.intList);
        this.intSet = new XHashSet<>(this);
        this.intSet.addAll(_o_.intSet);
        this.intDBItemMap = new XHashMap<>(this);
        _o_.intDBItemMap.forEach((_k_, _v_) -> this.intDBItemMap.put(_k_, _v_ == null ? null : new DBItemDataMysql(_v_, this.intDBItemMap)));
        this.obj1 = _o_.obj1 == null ? null : new DBItemDataMysql(_o_.obj1, this);
        this.bytes = _o_.bytes;
        this.dirty = false;
    }

    public void copyFrom(DBPlayerDataMysql _o_) {
        this.id = _o_.id;
        this.name = _o_.name;
        this.lv = _o_.lv;
        this.intIntMap = new XHashMap<>(this);
        this.intIntMap.putAll(_o_.intIntMap);
        this.intList = new XArrayList<>(this);
        this.intList.addAll(_o_.intList);
        this.intSet = new XHashSet<>(this);
        this.intSet.addAll(_o_.intSet);
        this.intDBItemMap = new XHashMap<>(this);
        _o_.intDBItemMap.forEach((_k_, _v_) -> this.intDBItemMap.put(_k_, _v_ == null ? null : new DBItemDataMysql(_v_, this.intDBItemMap)));
        this.obj1 = _o_.obj1 == null ? null : new DBItemDataMysql(_o_.obj1, this);
        if (this.obj1 != null) {
            this.obj1.setParent(this);
        }
        this.bytes = _o_.bytes;
        makeModify();
    }

    public int getId(){
        return this.id;
    }

    public void setId(int _v_){
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

    public int getLv(){
        return this.lv;
    }

    public void setLv(int _v_){
        this.lv = _v_;
        makeModify();
    }

    public java.util.Map<Integer, Integer> getIntIntMap(){
        return this.intIntMap;
    }

    public void setIntIntMap(XHashMap<Integer, Integer> _v_){
        this.intIntMap = _v_;
        if (_v_ != null) {
            _v_.setParent(this);
        }
        makeModify();
    }

    public java.util.List<Integer> getIntList(){
        return this.intList;
    }

    public void setIntList(XArrayList<Integer> _v_){
        this.intList = _v_;
        if (_v_ != null) {
            _v_.setParent(this);
        }
        makeModify();
    }

    public java.util.Set<Integer> getIntSet(){
        return this.intSet;
    }

    public void setIntSet(XHashSet<Integer> _v_){
        this.intSet = _v_;
        if (_v_ != null) {
            _v_.setParent(this);
        }
        makeModify();
    }

    public java.util.Map<Integer, DBItemDataMysql> getIntDBItemMap(){
        return this.intDBItemMap;
    }

    public void setIntDBItemMap(XHashMap<Integer, DBItemDataMysql> _v_){
        this.intDBItemMap = _v_;
        if (_v_ != null) {
            _v_.setParent(this);
        }
        makeModify();
    }

    public DBItemDataMysql getObj1(){
        return this.obj1;
    }

    public void setObj1(DBItemDataMysql _v_){
        this.obj1 = _v_;
        if (_v_ != null) {
            _v_.setParent(this);
        }
        makeModify();
    }

    public byte[] getBytes(){
        return this.bytes;
    }

    public void setBytes(byte[] _v_){
        this.bytes = _v_;
        makeModify();
    }

    @Override
    public void writeTo(OutputStreamBase out) throws IOException {
        out.writeInt(this.id);
        out.writeString(this.name);
        out.writeInt(this.lv);
        if (this.intIntMap == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(this.intIntMap.size());
            for (java.util.Map.Entry<Integer, Integer> _entry_0 : this.intIntMap.entrySet()) {
                out.write(_entry_0.getKey());
                out.write(_entry_0.getValue());
            }
        }
        if (this.intList == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(this.intList.size());
            for (Integer _v_1 : this.intList) {
                out.write(_v_1);
            }
        }
        if (this.intSet == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(this.intSet.size());
            for (Integer _v_2 : this.intSet) {
                out.write(_v_2);
            }
        }
        if (this.intDBItemMap == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(this.intDBItemMap.size());
            for (java.util.Map.Entry<Integer, DBItemDataMysql> _entry_3 : this.intDBItemMap.entrySet()) {
                out.write(_entry_3.getKey());
                out.writeBoolean(_entry_3.getValue() != null);
                if (_entry_3.getValue() != null) {
                    _entry_3.getValue().beforeWrite(out);
                    _entry_3.getValue().writeTo(out);
                    _entry_3.getValue().afterWrite(out);
                }
            }
        }
        out.writeBoolean(this.obj1 != null);
        if (this.obj1 != null) {
            this.obj1.beforeWrite(out);
            this.obj1.writeTo(out);
            this.obj1.afterWrite(out);
        }
        out.write(this.bytes);
    }

    @Override
    public void readFrom(InputStreamBase in) throws IOException {
        int _v_0 = in.readInt();
        this.id = _v_0;
        String _v_1 = in.readString();
        this.name = _v_1;
        int _v_2 = in.readInt();
        this.lv = _v_2;
        XHashMap<Integer, Integer> _v_3 = null;
        int _size_4 = in.readInt();
        if (_size_4 >= 0) {
            _v_3 = new XHashMap<>(this);
            for (int _i_ = 0; _i_ < _size_4; _i_++) {
                Integer _v_5 = (Integer) in.read();
                Integer _v_6 = (Integer) in.read();
                _v_3.put(_v_5, _v_6);
            }
        }
        this.intIntMap = _v_3;
        XArrayList<Integer> _v_7 = null;
        int _size_8 = in.readInt();
        if (_size_8 >= 0) {
            _v_7 = new XArrayList<>(this);
            for (int _i_ = 0; _i_ < _size_8; _i_++) {
                Integer _v_9 = (Integer) in.read();
                _v_7.add(_v_9);
            }
        }
        this.intList = _v_7;
        XHashSet<Integer> _v_10 = null;
        int _size_11 = in.readInt();
        if (_size_11 >= 0) {
            _v_10 = new XHashSet<>(this);
            for (int _i_ = 0; _i_ < _size_11; _i_++) {
                Integer _v_12 = (Integer) in.read();
                _v_10.add(_v_12);
            }
        }
        this.intSet = _v_10;
        XHashMap<Integer, DBItemDataMysql> _v_13 = null;
        int _size_14 = in.readInt();
        if (_size_14 >= 0) {
            _v_13 = new XHashMap<>(this);
            for (int _i_ = 0; _i_ < _size_14; _i_++) {
                Integer _v_15 = (Integer) in.read();
                DBItemDataMysql _v_16 = null;
                if (in.readBoolean()) {
                    _v_16 = new DBItemDataMysql();
                    _v_16.beforeRead(in);
                    _v_16.readFrom(in);
                    _v_16.afterRead(in);
                    _v_16.setParent(_v_13);
                }
                _v_13.put(_v_15, _v_16);
            }
        }
        this.intDBItemMap = _v_13;
        DBItemDataMysql _v_17 = null;
        if (in.readBoolean()) {
            _v_17 = new DBItemDataMysql();
            _v_17.beforeRead(in);
            _v_17.readFrom(in);
            _v_17.afterRead(in);
            _v_17.setParent(this);
        }
        this.obj1 = _v_17;
        byte[] _v_18 = (byte[]) in.read();
        this.bytes = _v_18;
        this.dirty = false;
    }

    @Override
    public String toString() {
        StringBuilder _sb_ = new StringBuilder(super.toString());
        _sb_.append("=(");
        _sb_.append("id=").append(id).append(",");
        _sb_.append("name=").append(name == null ? "null" : "T" + name.length()).append(",");
        _sb_.append("lv=").append(lv).append(",");
        _sb_.append("intIntMap=").append(intIntMap).append(",");
        _sb_.append("intList=").append(intList).append(",");
        _sb_.append("intSet=").append(intSet).append(",");
        _sb_.append("intDBItemMap=").append(intDBItemMap).append(",");
        _sb_.append("obj1=").append(obj1).append(",");
        _sb_.append("bytes=").append(bytes).append(",");
        _sb_.append(")");
        return _sb_.toString();
    }
}
