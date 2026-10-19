package org.evd.game.base;

/**
 * db数据库类
 * @author zenghongming
 * @date 2020/02/09 16:59
 */
public abstract class DirtyObject {
    /** 当前内存内容的单调修改版本；保存时不清零。 */
    public transient long memVersion;
    /** 最近收到数据库保存成功响应的内存版本。 */
    public transient long dbVersion;
    private transient DirtyObject _parent;

    public DirtyObject() {
        // 直接初始化的时候就设置1算了;
        //makeModify();
    }

    public DirtyObject(DirtyObject _parent) {
        this._parent = _parent;
        // makeModify();
    }


    /**
     * 谁变化就谁调用该方法
     */
    public void makeModify() {
        parentMakeModify();
    }

    private void parentMakeModify() {
        if (_parent != null && _parent != this) {
            _parent.parentMakeModify();
        }
        if(_parent == null) {
            memVersion++;
        }
    }


    public long getMemVersion() {
        return memVersion;
    }

    public void setMemVersion(long memVersion) {
        this.memVersion = memVersion;
    }

    public long getDbVersion() {
        return dbVersion;
    }

    public void setDbVersion(long dbVersion) {
        this.dbVersion = dbVersion;
    }

    public boolean isModified() {
        return memVersion != dbVersion;
    }

    public void setParent(DirtyObject parent) {
        if (_parent != null && parent != null && _parent != parent) {
            throw new DBException("MdbBean already has a parent _oldParentName : " + _parent.getClass().getName() + "newParentName : " + parent.getClass().getName());
        }
        this._parent = parent;
    }

    public DirtyObject findParent() {
        return _parent;
    }


    public Object marshalKey() {
        return "";
    }
    public Object marshalValue() {
        return "";
    }

}
