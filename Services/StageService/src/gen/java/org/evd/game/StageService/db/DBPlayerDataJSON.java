package org.evd.game.StageService.db;
import com.alibaba.fastjson2.annotation.JSONField;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.DbEntity.collection.XArrayList;
import org.evd.game.runtime.DbEntity.collection.XHashMap;
import org.evd.game.runtime.DbEntity.collection.XHashSet;

public final class DBPlayerDataJSON extends DirtyObject {
    @JSONField(name = "1")
    private String id;
    @JSONField(name = "2")
    private String name;
    @JSONField(name = "3")
    private int lv;
    @JSONField(name = "4")
    private XHashMap<Integer, Integer> intIntMap;
    @JSONField(name = "5")
    private XArrayList<Integer> intList;
    @JSONField(name = "6")
    private XHashSet<Integer> intSet;
    @JSONField(name = "7")
    private XHashMap<Integer, DBItemDataJSON> intDBItemMap;

    DBPlayerDataJSON(DirtyObject _xp_) {
        super(_xp_);
        this.id = "";
        this.name = "";
        this.intIntMap = new XHashMap<>(this);
        this.intList = new XArrayList<>(this);
        this.intSet = new XHashSet<>(this);
        this.intDBItemMap = new XHashMap<>(this);
    }

    public DBPlayerDataJSON() {
        this((DirtyObject)null);
    }

    public DBPlayerDataJSON(DBPlayerDataJSON _o_) {
        this(_o_, null);
    }

    DBPlayerDataJSON(DBPlayerDataJSON _o_, DirtyObject _xp_) {
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
        _o_.intDBItemMap.forEach((_k_, _v_) -> this.intDBItemMap.put(_k_, _v_ == null ? null : new DBItemDataJSON(_v_, this.intDBItemMap)));
        this.dirty = false;
    }

    public void copyFrom(DBPlayerDataJSON _o_) {
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
        _o_.intDBItemMap.forEach((_k_, _v_) -> this.intDBItemMap.put(_k_, _v_ == null ? null : new DBItemDataJSON(_v_, this.intDBItemMap)));
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

    public java.util.Map<Integer, DBItemDataJSON> getIntDBItemMap(){
        return this.intDBItemMap;
    }

    public void setIntDBItemMap(XHashMap<Integer, DBItemDataJSON> _v_){
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
