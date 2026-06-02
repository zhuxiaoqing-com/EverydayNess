package org.evd.game.runtime.Db.collection;


import org.evd.game.base.DirtyObject;

import java.util.HashSet;

public class XHashSet<E> extends TrackedSet<E> {

    public XHashSet() {
        super(new HashSet<>(), null);
    }

    public XHashSet(DirtyObject _xp_) {
        super(new HashSet<>(), _xp_);
    }
}
