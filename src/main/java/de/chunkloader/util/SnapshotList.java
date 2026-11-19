package de.chunkloader.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.UnaryOperator;

public final class SnapshotList<E> extends AbstractList<E> implements RandomAccess {
    private final List<E> snapshot;
    private List<E> edited;

    public SnapshotList(List<E> snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot);
    }

    private List<E> current() { return edited == null ? snapshot : edited; }
    private List<E> mutable() {
        if (edited == null) edited = new ArrayList<>(snapshot);
        return edited;
    }

    @Override public E get(int index) { return current().get(index); }
    @Override public int size() { return current().size(); }
    @Override public Object[] toArray() { return current().toArray(); }
    @Override public <T> T[] toArray(T[] array) { return current().toArray(array); }
    @Override public E set(int index, E value) { return mutable().set(index, value); }
    @Override public void add(int index, E value) { mutable().add(index, value); modCount++; }
    @Override public E remove(int index) { E old = mutable().remove(index); modCount++; return old; }
    @Override public boolean addAll(int index, Collection<? extends E> values) {
        boolean changed = mutable().addAll(index, values);
        if (changed) modCount++;
        return changed;
    }
    @Override public void clear() {
        if (!isEmpty()) { edited = new ArrayList<>(); modCount++; }
    }
    @Override protected void removeRange(int from, int to) {
        mutable().subList(from, to).clear();
        if (from != to) modCount++;
    }
    @Override public void sort(Comparator<? super E> comparator) { mutable().sort(comparator); modCount++; }
    @Override public void replaceAll(UnaryOperator<E> operator) { mutable().replaceAll(operator); modCount++; }
}
