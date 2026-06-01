package org.evd.game.Db.collection;


import org.evd.game.base.DirtyObject;

import java.util.HashSet;

public class XHashSet<E> extends TrackedSet<E> {

    public XHashSet(DirtyObject _xp_) {
        super(new HashSet<>(), _xp_);
    }
}
