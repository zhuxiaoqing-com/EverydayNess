package org.evd.game.runtime.DbEntity.collection;


import org.evd.game.base.DirtyObject;

import java.util.HashMap;
import java.util.HashSet;

public class XHashSet<E> extends TrackedSet<E> {

    public XHashSet(DirtyObject _xp_) {
        super(new HashSet<>(), _xp_);
    }
}
