package edu.ucsd.arcum.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class Subset<T> implements Iterable<T>
{
    private Collection<?> collection;
    private Class<T> clazz;

    // shorthand to avoid saying the type twice
    public static <X, T extends X> Subset<T> subset(Collection<X> givenCollection,
        Class<T> clazz)
    {
        return new Subset<T>(givenCollection, clazz);
    }

    public Subset(Collection<?> givenCollection, Class<T> clazz) {
        this.collection = givenCollection;
        this.clazz = clazz;
    }

    public Iterator<T> iterator() {
        ArrayList<T> subset = extractSubset();
        return subset.iterator();
    }

    @Override
    public String toString() {
        return extractSubset().toString();
    }

    private ArrayList<T> extractSubset() {
        ArrayList<T> subset = new ArrayList<T>();
        for (Object element : collection) {
            if (element != null && clazz.isAssignableFrom(element.getClass())) {
                subset.add(clazz.cast(element));
            }
        }
        return subset;
    }
}