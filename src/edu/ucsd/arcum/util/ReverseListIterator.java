package edu.ucsd.arcum.util;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class ReverseListIterator<T> implements ListIterator<T>, Iterable<T>
{
    private final ListIterator<T> it;

    public ReverseListIterator(List<T> list) {
        this.it = list.listIterator();
        while (it.hasNext()) {
            it.next();
        }
    }

    @Override
    public void add(T val) {
        it.add(val);
    }

    @Override
    public boolean hasNext() {
        return it.hasPrevious();
    }

    @Override
    public boolean hasPrevious() {
        return it.hasNext();
    }

    @Override
    public T next() {
        return it.previous();
    }

    @Override
    public int nextIndex() {
        return it.previousIndex();
    }

    @Override
    public T previous() {
        return it.next();
    }

    @Override
    public int previousIndex() {
        return it.nextIndex();
    }

    @Override
    public void remove() {
        it.remove();
    }

    @Override
    public void set(T val) {
        it.set(val);
    }

    @Override
    public Iterator<T> iterator() {
        return this;
    }
}
