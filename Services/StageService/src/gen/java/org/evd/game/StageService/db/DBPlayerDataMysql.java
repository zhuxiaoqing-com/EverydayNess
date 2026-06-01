package org.evd.game.StageService.db;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.Db.collection.XArrayList;
import org.evd.game.runtime.Db.collection.XHashMap;
import org.evd.game.runtime.Db.collection.XHashSet;

public final class DBPlayerDataMysql extends DirtyObject {
    private String id;
    private String name;
    private int lv;
    private XHashMap<Integer, Integer> intIntMap;
    private XArrayList<Integer> intList;
    private XHashSet<Integer> intSet;
    private XHashMap<Integer, DBItemDataMysql> intDBItemMap;

    DBPlayerDataMysql(DirtyObject _xp_) {
        super(_xp_);
        this.id = "";
        this.name = "";
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
        makeModify();
    }

    public String getId(){
        return this.id;
    }

    public void setId(String _v_){
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

    @Override
    public String toString() {
        StringBuilder _sb_ = new StringBuilder(super.toString());
        _sb_.append("=(");
        _sb_.append("id=").append(id == null ? "null" : "T" + id.length()).append(",");
        _sb_.append("name=").append(name == null ? "null" : "T" + name.length()).append(",");
        _sb_.append("lv=").append(lv).append(",");
        _sb_.append("intIntMap=").append(intIntMap).append(",");
        _sb_.append("intList=").append(intList).append(",");
        _sb_.append("intSet=").append(intSet).append(",");
        _sb_.append("intDBItemMap=").append(intDBItemMap).append(",");
        _sb_.append(")");
        return _sb_.toString();
    }
}
