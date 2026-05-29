package org.evd.game.DbEntity.collection;



import org.evd.game.base.DirtyObject;
import org.evd.game.base.DBException;

import java.util.*;
import java.util.function.UnaryOperator;

public class TrackedList<E> extends DirtyObject implements List<E>,RandomAccess {
    private List<E> delegate;

    public TrackedList() {
    }

    public TrackedList(List<E> delegate, DirtyObject _xp_) {
        this.delegate = Objects.requireNonNull(delegate);
        setParent(_xp_);
    }

/*    public boolean isModified() { return !modifications.isEmpty(); }
    public boolean isModified(ModifyType type) { return modifications.containsKey(type); }
    public Object getModifiedValue(ModifyType type) { return modifications.get(type); }
    public void clearModified() { modifications.clear(); }
    public void clearModified(ModifyType type) { modifications.remove(type); }*/

  /*  @Override
    public boolean fieldAnyModify() {
        if(only_isModify()) {
            return true;
        }
        if (delegate.isEmpty()) {
            return false;
        }
        E e1 = delegate.get(0);
        if (!(e1 instanceof AbsDBEntity)) {
            return false;
        }
        for (E e : delegate) {
            if (((AbsDBEntity) e).only_isModify()) {
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
        E e1 = delegate.get(0);
        if (!(e1 instanceof AbsDBEntity)) {
            return false;
        }
        for (E e : delegate) {
            if (((AbsDBEntity) e).checkVersion(checkPointCount)) {
                Mdb.logger.error("checkFieldVersion checkVersion true checkPointCount {} currVersion {} class {} ",
                        checkPointCount, findVersion(checkPointCount), getClass().getName(), new RuntimeException());
                return true;
            }
        }

        for (E e : delegate) {
            if (((AbsDBEntity) e).checkFieldVersion(checkPointCount)) {
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

    // 修改方法重写
    @Override
    public boolean add(E e) {
        boolean result = delegate.add(e);
        if (result) {
            markModified(ModifyType.ADD, e);
        }
        return result;
    }

    @Override
    public void add(int index, E element) {
        delegate.add(index, element);
        markModified(ModifyType.ADD, element);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean result = delegate.addAll(c);
        if (result) {
            for (E e : c) {
                markModified(ModifyType.ADD, e);
            }
        }
        return result;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        boolean result = delegate.addAll(index, c);
        if (result) {
            for (E e : c) {
                markModified(ModifyType.ADD, e);
            }
        }
        return result;
    }

    @Override
    public void clear() {
        if (!isEmpty()) {
            for (E e : delegate) {
                markModified(ModifyType.REMOVE, e);
            }
            delegate.clear();
        }
    }

    @Override
    public E remove(int index) {
        E removed = delegate.remove(index);
        markModified(ModifyType.REMOVE, removed);
        return removed;
    }

    @Override
    public boolean remove(Object o) {
        boolean changed = delegate.remove(o);
        if (changed) {
            markModified(ModifyType.REMOVE, o);
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        for (Object o : c) {
            markModified(ModifyType.REMOVE, o);
        }
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = delegate.retainAll(c);
        if (!changed) {
            return false;
        }
        markModified(ModifyType.REMOVE, null);
        return true;
    }

    @Override
    public E set(int index, E element) {
        E old = delegate.set(index, element);
        if (!Objects.equals(old, element)) {
            markModified(ModifyType.REMOVE, old);
            markModified(ModifyType.ADD, element);
        }
        return old;
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        delegate.replaceAll(operator);
        markModified(ModifyType.CHANGE, null);
    }

    @Override
    public void sort(Comparator<? super E> c) {
        delegate.sort(c);
        markModified(ModifyType.CHANGE, null);
    }


    // 只读方法委托
    @Override
    public E get(int index) {
        return delegate.get(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return new TrackedIterator(delegate.iterator());
    }

    @Override
    public ListIterator<E> listIterator() {
        return new TrackedListIterator(delegate.listIterator());
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new TrackedListIterator(delegate.listIterator(index));
    }

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
    public int indexOf(Object o) {
        return delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return delegate.lastIndexOf(o);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return Collections.unmodifiableList(delegate.subList(fromIndex, toIndex));
    }

    @Override
    public Spliterator<E> spliterator() {
       throw new DBException("Not implemented spliterator");
    }

    private class TrackedIterator implements Iterator<E> {
        private final Iterator<E> it;
        private E lastReturned;

        TrackedIterator(Iterator<E> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public E next() {
            return lastReturned = it.next();
        }

        @Override
        public void remove() {
            it.remove();
            markModified(ModifyType.REMOVE, lastReturned);
        }
    }

    private class TrackedListIterator implements ListIterator<E> {
        private final ListIterator<E> it;
        private E lastReturned;

        TrackedListIterator(ListIterator<E> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public E next() {
            return lastReturned = it.next();
        }

        @Override
        public boolean hasPrevious() {
            return it.hasPrevious();
        }

        @Override
        public E previous() {
            return lastReturned = it.previous();
        }

        @Override
        public int nextIndex() {
            return it.nextIndex();
        }

        @Override
        public int previousIndex() {
            return it.previousIndex();
        }

        @Override
        public void remove() {
            it.remove();
            markModified(ModifyType.REMOVE, lastReturned);
        }

        @Override
        public void set(E e) {
            E old = lastReturned;
            it.set(e);
            if (!Objects.equals(old, e)) {
                markModified(ModifyType.REMOVE, old);
                markModified(ModifyType.ADD, e);
            }
        }

        @Override
        public void add(E e) {
            it.add(e);
            markModified(ModifyType.ADD, e);
        }
    }

    /***
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        List<?> target;
        if (o instanceof TrackedList<?>) {
            target = ((TrackedList<?>) o).delegate;
        } else if (o instanceof List<?>) {
            target = (List<?>) o;
        } else {
            return false;
        }
        if (delegate.size() != target.size()) {
            return false;
        }
        Iterator<E> thisIterator = delegate.iterator();
        Iterator<?> targetIterator = target.iterator();
        while (thisIterator.hasNext()) {
            if (!Objects.equals(thisIterator.next(), targetIterator.next())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        for (E e : delegate) {
            hashCode = 31 * hashCode + Objects.hashCode(e);
        }
        return hashCode;
    }

    @Override
    public String toString() {
        return String.valueOf(delegate);
    }
    ***/

}
