package org.evd.game.StageService.dbEntity;
import com.alibaba.fastjson2.annotation.JSONField;
import org.evd.game.base.DirtyObject;
import org.evd.game.runtime.DbEntity.collection.XArrayList;
import org.evd.game.runtime.DbEntity.collection.XHashMap;
import org.evd.game.runtime.DbEntity.collection.XHashSet;


public final class DBPlayer extends DirtyObject {
    private long id;
    private String name;
    private int lv;
    private XHashMap<Integer, Integer> intIntMap;
    private XArrayList<Integer> intList;
    private XHashSet<Integer> intSet;
    private XHashMap<Integer, DBItem> intDBItemMap;

    DBPlayer(DirtyObject _xp_) {
        super(_xp_);
		this.name = "";
		this.intIntMap = new XHashMap<Integer, Integer>(this);
		this.intList = new XArrayList<Integer>(this);
		this.intSet = new XHashSet<>(this);
		this.intDBItemMap = new XHashMap<Integer, DBItem>(this);
	}

	public DBPlayer() {
		this((DirtyObject)null);
	}

	public DBPlayer(DBPlayer _o_) {
		this(_o_, null);
	}

	DBPlayer(DBPlayer _o_, DirtyObject _xp_) {
		super(_xp_);
		this.id = _o_.id;
		this.name = _o_.name;
		this.lv = _o_.lv;
		this.intIntMap = new XHashMap<Integer, Integer>(this);
		_o_.intIntMap.forEach((_k_, _v_) -> this.intIntMap.put(_k_, _v_));
		this.intList = new XArrayList<Integer>(this);
		_o_.intList.forEach(_v_ -> this.intList.add(_v_));
		this.intSet = new XHashSet<>(this);
		_o_.intSet.forEach(_v_ -> this.intSet.add(_v_));
		this.intDBItemMap = new XHashMap<Integer, DBItem>(this);
		_o_.intDBItemMap.forEach((_k_, _v_) -> this.intDBItemMap.put(_k_, new DBItem(_v_)));
	}

	public void copyFrom(DBPlayer _o_) {
        this.id = _o_.id;
        this.name = _o_.name;
        this.lv = _o_.lv;
        this.intIntMap = new XHashMap<Integer, Integer>(this);
		_o_.intIntMap.forEach((_k_, _v_) -> this.intIntMap.put(_k_, _v_));
        this.intList = new XArrayList<Integer>(this);
		_o_.intList.forEach(_v_ -> this.intList.add(_v_));
        this.intSet = new XHashSet<>(this);
		_o_.intSet.forEach(_v_ -> this.intSet.add((_v_)));
        this.intDBItemMap = new XHashMap<Integer, DBItem>(this);
		_o_.intDBItemMap.forEach((_k_, _v_) -> this.intDBItemMap.put(_k_, new DBItem(_v_)));
		makeModify();
	}


	@JSONField(name = "1")
	public long getId(){
		return this.id;
	}

	@JSONField(name = "2")
	public String getName(){
		return this.name;
	}

	@JSONField(name = "3")
	public int getLv(){
		return this.lv;
	}

	@JSONField(name = "4")
	public java.util.Map<Integer, Integer> getIntIntMap(){
		return this.intIntMap;
	}

	@JSONField(name = "5")
	public java.util.List<Integer> getIntList(){
		return this.intList;
	}

	@JSONField(name = "6")
	public java.util.Set<Integer> getIntSet(){
		return this.intSet;
	}

	@JSONField(name = "7")
	public java.util.Map<Integer, DBItem> getIntDBItemMap(){
		return this.intDBItemMap;
	}

	@JSONField(name = "1")
	public void setId(long _v_){
		this.id = _v_;
		makeModify();
	}

	@JSONField(name = "2")
	public void setName(String _v_){
		this.name = _v_;
		makeModify();
	}

	@JSONField(name = "3")
	public void setLv(int _v_){
		this.lv = _v_;
		makeModify();
	}

	@JSONField(name = "4")
	public void setIntIntMap(XHashMap<Integer, Integer> _v_){
		this.intIntMap = _v_;
		_v_.setParent(this);
		makeModify();
	}

	@JSONField(name = "5")
	public void setIntList(XArrayList<Integer> _v_){
		this.intList = _v_;
		_v_.setParent(this);
		makeModify();
	}

	@JSONField(name = "6")
	public void setIntSet(XHashSet<Integer> _v_){
		this.intSet = _v_;
		_v_.setParent(this);
		makeModify();
	}

	@JSONField(name = "7")
	public void setIntDBItemMap(XHashMap<Integer, DBItem> _v_){
		this.intDBItemMap = _v_;
		_v_.setParent(this);
		makeModify();
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append("id=").append(id).append(",");
		_sb_.append("name=").append("T").append(name.length()).append(",");
		_sb_.append("lv=").append(lv).append(",");
		_sb_.append("intIntMap=").append(intIntMap).append(",");
		_sb_.append("intList=").append(intList).append(",");
		_sb_.append("intSet=").append(intSet).append(",");
		_sb_.append("intDBItemMap=").append(intDBItemMap).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

}
