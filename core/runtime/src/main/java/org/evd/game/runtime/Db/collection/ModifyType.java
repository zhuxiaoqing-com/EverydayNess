package org.evd.game.runtime.Db.collection;

public enum ModifyType {
    ADD(Object.class),
    REMOVE(Object.class),
    CHANGE(Void.class);

    public final Class<?> valueType;
    ModifyType(Class<?> valueType) {
        this.valueType = valueType;
    }
}
