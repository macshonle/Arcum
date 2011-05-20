package edu.ucsd.arcum.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

public class ListUtil
{
    public static <T> Collection<T> intersection(Collection<? extends T> a,
        Collection<? extends T> b)
    {
        Collection<T> result = new ArrayList<T>();
        for (T element : a) {
            if (b.contains(element)) {
                result.add(element);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.printf("%s%n", crossProduct(Lists.newArrayList(
            Lists.newArrayList(1, 2),
            Lists.newArrayList(3, 4))));
        System.out.printf("%s%n", crossProduct(Lists.newArrayList(
            Lists.newArrayList(1, 2),
            Lists.newArrayList(3, 4),
            Lists.newArrayList(5, 6))));
        System.out.printf("%s%n", crossProduct(Lists.newArrayList(
            Lists.newArrayList(1),
            Lists.newArrayList(3, 4),
            Lists.newArrayList(5, 6, 7))));
    }
    
    // [[1 2] [3 4]] => [[1 3] [1 4] [2 3] [2 4]]
    public static <T, L extends List<T>> List<L> crossProduct(List<L> lists) {
        List<L> result = Lists.newArrayList();
        if (lists.size() == 1) {
            List<T> ts = lists.get(0);
            for (T t: ts) {
                L newArrayList = (L)Lists.newArrayList(t);
                result.add(newArrayList);
            }
        }
        else if (lists.size() > 0) {
            Iterator<L> it = lists.iterator();
            List<T> first = it.next();
            List<L> rest = Lists.newArrayList(it);

            Collection<L> restCrossProduct = crossProduct(rest);
            for (T tupleHead : first) {
                for (L tupleRest : restCrossProduct) {
                    L tuple = (L)Lists.newArrayList(tupleHead);
                    tuple.addAll(tupleRest);
                    result.add(tuple);
                }
            }
        }
        return result;
    }
    
    // Apply the predicate to the elements of "inList". If any return true, short
    // circuit and return true.
    public static <T> boolean disjunctReduce(List<T> inList, Predicate<T> predicate) {
        for (T element : inList) {
            boolean b = predicate.apply(element);
            if (b) {
                return true;
            }
        }
        return false;
    }

    public static <T> List<T> reverse(List<T> list) {
        List<T> result = new ArrayList<T>(list.size());
        for (T t : new ReverseListIterator<T>(list)) {
            result.add(t);
        }
        return result;
    }
}
