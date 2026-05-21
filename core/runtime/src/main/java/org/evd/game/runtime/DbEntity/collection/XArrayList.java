package org.evd.game.runtime.DbEntity.collection;


import org.evd.game.base.DBException;
import org.evd.game.base.DirtyObject;

import java.util.*;
import java.util.function.UnaryOperator;

public class XArrayList<E> extends TrackedList<E> {

    public XArrayList(DirtyObject _xp_) {
        super(new ArrayList<>(), _xp_);
    }
}
