package org.evd.game.Db.collection;



import org.evd.game.base.DirtyObject;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TrackedMap<K, V> extends DirtyObject implements Map<K, V> {
    private final Map<K, V> delegate;


    public TrackedMap(Map<K, V> delegate, DirtyObject _xp_) {
        this.delegate = Objects.requireNonNull(delegate);
        setParent(_xp_);
    }

  /*  @Override
    public boolean fieldAnyModify() {
      if(only_isModify()) {
            return true;
        }
        if (delegate.isEmpty()) {
            return false;
        }
        V e1 = delegate.values().iterator().next();
        if (!(e1 instanceof AbsDBEntity)) {
            return false;
        }
        for (V e : delegate.values()) {
            if (((AbsDBEntity) e).only_isModify()) {
                Mdb.logger.error("fieldAnyModify selfModify false fieldModify true class {} ", getClass().getName(), new RuntimeException());
                return true;
            }
        }
        return false;
    }*/

/*
    public boolean checkFieldVersion(long checkPointCount) {
        if (delegate.isEmpty()) {
            return false;
        }
        V e1 = delegate.values().iterator().next();
        if (!(e1 instanceof AbsDBEntity)) {
            return false;
        }

        for (V e : delegate.values()) {
            if (((AbsDBEntity) e).checkVersion(checkPointCount)) {
                Mdb.logger.error("checkFieldVersion checkVersion true checkPointCount {} currVersion {} class {} ",
                        checkPointCount, findVersion(checkPointCount), getClass().getName(), new RuntimeException());
                return true;
            }
        }

        for (V e : delegate.values()) {
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

    private void markChanged() {
        markModified(ModifyType.CHANGE, null);
    }

    private void rebindValue(V oldValue, V newValue) {
        if (oldValue != newValue) {
            if (oldValue != null) {
                markModified(ModifyType.REMOVE, oldValue);
            }
            if (newValue != null) {
                markModified(ModifyType.ADD, newValue);
            }
        } else {
            markChanged();
        }
    }

    @Override
    public V put(K key, V value) {
        V old = delegate.put(key, value);
        rebindValue(old, value);
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        delegate.putAll(m);
        for (V value : m.values()) {
            markModified(ModifyType.ADD, value);
        }
    }

    @Override
    public V remove(Object key) {
        V remove = delegate.remove(key);
        if (remove != null) {
            markModified(ModifyType.REMOVE, remove);
        }
        return remove;
    }

    @Override
    public void clear() {
        if (!isEmpty()) {
            for (V e : delegate.values()) {
                markModified(ModifyType.REMOVE, e);
            }
            delegate.clear();
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V old = delegate.putIfAbsent(key, value);
        if (old == null) {
            markModified(ModifyType.ADD, value);
        } else {
            markChanged();
        }
        return old;
    }

    @Override
    public boolean remove(Object key, Object value) {
        V old = delegate.get(key);
        boolean changed = delegate.remove(key, value);
        if (changed) {
            markModified(ModifyType.REMOVE, old != null ? old : value);
        }
        return changed;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        V current = delegate.get(key);
        boolean changed = delegate.replace(key, oldValue, newValue);
        if (changed) {
            rebindValue(current != null ? current : oldValue, newValue);
        }
        return changed;
    }

    @Override
    public V replace(K key, V value) {
        if (containsKey(key)) {
            V old = delegate.replace(key, value);
            rebindValue(old, value);
            return old;
        }
        return null;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
        V oldValue = delegate.get(key);
        V newValue = delegate.compute(key, remapping);
        rebindValue(oldValue, newValue);
        return newValue;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mapping) {
        V oldValue = delegate.get(key);
        V value = delegate.computeIfAbsent(key, mapping);
        if (oldValue == null && value != null) {
            markModified(ModifyType.ADD, value);
        } else {
            markChanged();
        }
        return value;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
        V oldValue = delegate.get(key);
        V newValue = delegate.computeIfPresent(key, remapping);
        if (oldValue != null) {
            rebindValue(oldValue, newValue);
        }
        return newValue;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
        V oldValue = delegate.get(key);
        V newValue = delegate.merge(key, value, remapping);
        rebindValue(oldValue, newValue);
        return newValue;
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Map<K, V> oldSnapshot = new HashMap<>(delegate);
        delegate.replaceAll(function);
        for (Entry<K, V> entry : oldSnapshot.entrySet()) {
            rebindValue(entry.getValue(), delegate.get(entry.getKey()));
        }
    }

    // 只读方法委托
    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return delegate.get(key);
    }

    @Override
    public Set<K> keySet() {
        return new TrackedKeySet();
    }

    @Override
    public Collection<V> values() {
        return new TrackedValues();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new TrackedEntrySet();
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        delegate.forEach(action);
    }


    private class TrackedKeySet extends AbstractSet<K> {
        @Override
        public Iterator<K> iterator() {
            return new TrackedKeyIterator(delegate.keySet().iterator());
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return TrackedMap.this.remove(o) != null;
        }

        @Override
        public void clear() {
            TrackedMap.this.clear();
        }
    }

    private class TrackedValues extends AbstractCollection<V> {
        @Override
        public Iterator<V> iterator() {
            return new TrackedValueIterator(delegate.values().iterator());
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.containsValue(o);
        }

        @Override
        public void clear() {
            TrackedMap.this.clear();
        }
    }

    private class TrackedEntrySet extends AbstractSet<Entry<K, V>> {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new TrackedEntryIterator(delegate.entrySet().iterator());
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.entrySet().contains(o);
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }
            Entry<?, ?> entry = (Entry<?, ?>) o;
            Object key = entry.getKey();
            Object value = entry.getValue();
            return TrackedMap.this.remove(key, value);
        }

        @Override
        public void clear() {
            TrackedMap.this.clear();
        }
    }


    private class TrackedKeyIterator implements Iterator<K> {
        private final Iterator<K> it;
        private K lastKey;
        private V lastValue;

        TrackedKeyIterator(Iterator<K> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public K next() {
            lastKey = it.next();
            lastValue = delegate.get(lastKey);
            return lastKey;
        }

        @Override
        public void remove() {
            it.remove();
            markModified(ModifyType.REMOVE, lastValue);
        }
    }

    private class TrackedValueIterator implements Iterator<V> {
        private final Iterator<V> it;
        private V lastReturned;

        TrackedValueIterator(Iterator<V> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public V next() {
            return lastReturned = it.next();
        }

        @Override
        public void remove() {
            it.remove();
            markModified(ModifyType.REMOVE, lastReturned);
        }
    }


    private class TrackedEntryIterator implements Iterator<Entry<K, V>> {
        private final Iterator<Entry<K, V>> it;
        private Entry<K, V> lastReturned;

        TrackedEntryIterator(Iterator<Entry<K, V>> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public Entry<K, V> next() {
            lastReturned = it.next();
            return new TrackedMapEntry(lastReturned);
        }

        @Override
        public void remove() {
            it.remove();
            markModified(ModifyType.REMOVE, lastReturned.getValue());
        }
    }

    private class TrackedMapEntry implements Entry<K, V> {
        private final Entry<K, V> entry;

        TrackedMapEntry(Entry<K, V> entry) {
            this.entry = entry;
        }

        @Override
        public K getKey() {
            return entry.getKey();
        }

        @Override
        public V getValue() {
            return entry.getValue();
        }

        @Override
        public V setValue(V value) {
            V old = entry.setValue(value);

            markModified(ModifyType.REMOVE, old);
            markModified(ModifyType.ADD, value);
            return old;
        }

        @Override
        public boolean equals(Object o) {
            return entry.equals(o);
        }

        @Override
        public int hashCode() {
            return entry.hashCode();
        }
    }

    /***
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        Map<?, ?> target;
        if (o instanceof TrackedMap<?, ?>) {
            target = ((TrackedMap<?, ?>) o).delegate;
        } else if (o instanceof Map<?, ?>) {
            target = (Map<?, ?>) o;
        } else {
            return false;
        }
        if (delegate.size() != target.size()) {
            return false;
        }
        try {
            for (Entry<K, V> entry : delegate.entrySet()) {
                K key = entry.getKey();
                V value = entry.getValue();
                Object targetValue = target.get(key);
                if (!Objects.equals(value, targetValue)) {
                    return false;
                }
                if (value == null && !target.containsKey(key)) {
                    return false;
                }
            }
            return true;
        } catch (ClassCastException | NullPointerException ignored) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (Entry<K, V> entry : delegate.entrySet()) {
            hash += Objects.hashCode(entry);
        }
        return hash;
    }

    @Override
    public String toString() {
        return String.valueOf(delegate);
    }
    ***/
}
