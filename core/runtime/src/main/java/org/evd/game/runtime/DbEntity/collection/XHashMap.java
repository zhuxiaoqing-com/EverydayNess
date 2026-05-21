package org.evd.game.runtime.DbEntity.collection;


import org.evd.game.base.DirtyObject;

import java.util.ArrayList;
import java.util.HashMap;

public class XHashMap<K,V> extends TrackedMap<K,V> {

    public XHashMap(DirtyObject _xp_) {
        super(new HashMap<>(), _xp_);
    }
}
