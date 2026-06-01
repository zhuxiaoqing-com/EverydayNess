package org.evd.game.runtime.Db.collection;


import org.evd.game.base.DirtyObject;

import java.util.*;

public class XArrayList<E> extends TrackedList<E> {

    public XArrayList(DirtyObject _xp_) {
        super(new ArrayList<>(), _xp_);
    }
}
