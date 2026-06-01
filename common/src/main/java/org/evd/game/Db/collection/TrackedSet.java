package org.evd.game.Db.collection;



import org.evd.game.base.DBException;
import org.evd.game.base.DirtyObject;

import java.util.*;

public class TrackedSet<E> extends DirtyObject implements Set<E> {
    private final Set<E> delegate;

    public TrackedSet(Set<E> delegate, DirtyObject _xp_) {
        this.delegate = Objects.requireNonNull(delegate);
        setParent(_xp_);
    }
/*

    @Override public boolean isModified() { return !modifications.isEmpty(); }
    @Override public boolean isModified(ModifyType type) { return modifications.containsKey(type); }
    @Override public Object getModifiedValue(ModifyType type) { return modifications.get(type); }
    @Override public void clearModified() { modifications.clear(); }
    @Override public void clearModified(ModifyType type) { modifications.remove(type); }
*/

 /*   @Override
    public boolean fieldAnyModify() {
        // 自己有变化
        if(only_isModify()) {
            return true;
        }
        if (delegate.isEmpty()) {
            return false;
        }
        E e1 = delegate.iterator().next();
        if (!(e1 instanceof DirtyObject)) {
            return false;
        }
        for (E e : delegate) {
            if (((DirtyObject) e).only_isModify()) {
                Mdb.logger.error("fieldAnyModify selfModify false fieldModify true class {} ", getClass().getName(), new RuntimeException());
                return true;
            }
        }
        return false;
    }*/

/*
    @Override
    public boolean checkFieldVersion(long checkPointCount) {
        if (delegate.isEmpty()) {
            return false;
        }
        E e1 = delegate.iterator().next();
        if (!(e1 instanceof DirtyObject)) {
            return false;
        }
        for (E e : delegate) {
            if (((DirtyObject) e).checkVersion(checkPointCount)) {
                Mdb.logger.error("checkFieldVersion checkVersion true checkPointCount {} currVersion {} class {} ",
                        checkPointCount, findVersion(checkPointCount), getClass().getName(), new RuntimeException());
                return true;
            }
        }

        for (E e : delegate) {
            if (((DirtyObject) e).checkFieldVersion(checkPointCount)) {
                return true;
            }
        }
        return false;
    }
*/
    /**
     * @param type  type
     * @param value value有个准则就是如果是外面传入进来的，或者需要传出去的需要显示调用setParent; 原本就在里面的可以不调用
     */
    private void markModified(ModifyType type, Object value) {
        makeModify();
        if (value instanceof DirtyObject) {
            switch (type) {
                case ADD:
                    ((DirtyObject) value).setParent(this);
                    break;
                case REMOVE:
                    ((DirtyObject) value).setParent(null);
                    break;
                case CHANGE:
                    break;
            }
        }
    }

    @Override public boolean add(E e) {
        boolean changed = delegate.add(e);
        if (changed) {
            markModified(ModifyType.ADD, e);
        }
        return changed;
    }

    @Override public boolean addAll(Collection<? extends E> c) {
        for (E e : c) {
            markModified(ModifyType.ADD, e);
        }
        return delegate.addAll(c);
    }

    @Override public boolean remove(Object o) {
        boolean changed = delegate.remove(o);
        if (changed) {
            markModified(ModifyType.REMOVE, o);
        }
        return changed;
    }

    @Override public boolean removeAll(Collection<?> c) {
        boolean changed = delegate.removeAll(c);
        if (!changed) {
            return false;
        }
        for (Object o : c) {
            markModified(ModifyType.REMOVE, o);
        }
        return true;
    }

    @Override public boolean retainAll(Collection<?> c) {
        boolean changed = delegate.retainAll(c);
        if (!changed) {
            return false;
        }
        markModified(ModifyType.REMOVE, null);
        return true;
    }

    @Override public void clear() {
        if (!isEmpty()) {
            for (E e : delegate) {
                markModified(ModifyType.REMOVE, e);
            }
            delegate.clear();
        }
    }

    // 只读方法委托
    @Override public Iterator<E> iterator() { return new TrackedIterator(delegate.iterator()); }
    @Override public int size() { return delegate.size(); }
    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public boolean contains(Object o) { return delegate.contains(o); }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public Spliterator<E> spliterator() {
        throw new DBException("Not implemented spliterator");
    }

    private class TrackedIterator implements Iterator<E> {
        private final Iterator<E> it;
        private E lastReturned;
        TrackedIterator(Iterator<E> it) { this.it = it; }
        @Override public boolean hasNext() { return it.hasNext(); }
        @Override public E next() { return lastReturned = it.next(); }
        @Override public void remove() {
            it.remove();
            markModified(ModifyType.REMOVE, lastReturned);
        }
    }

    /***
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        Set<?> target;
        if (o instanceof TrackedSet<?>) {
            target = ((TrackedSet<?>) o).delegate;
        } else if (o instanceof Set<?>) {
            target = (Set<?>) o;
        } else {
            return false;
        }
        if (delegate.size() != target.size()) {
            return false;
        }
        try {
            return delegate.containsAll(target);
        } catch (ClassCastException | NullPointerException ignored) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (E e : delegate) {
            hash += Objects.hashCode(e);
        }
        return hash;
    }

    @Override
    public String toString() {
        return String.valueOf(delegate);
    }
    ***/
}
