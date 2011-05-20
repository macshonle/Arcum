package edu.ucsd.arcum.util;

import java.util.Stack;

/**
 * To use this class, place a private static DynamicScope field in your class,
 * to implement a combination of thread local storage and a shadow stack, similar
 * in implementation and spirit to AspectJ's cflow construct as utilized by the
 * so-called "Wormhole Pattern".
 */
public class DynamicScope<T>
{
    private ThreadLocal<Stack<T>> instance;

    public static <T> DynamicScope<T> newInstance() {
        return new DynamicScope<T>();
    }
    
    private DynamicScope() {
        this.instance = new ThreadLocal<Stack<T>>() {
            @Override
            protected Stack<T> initialValue() {
                return new Stack<T>();
            }
        };
    }
    
    public void push(T t) {
        instance.get().push(t);
    }
    
    public void pop() {
        instance.get().pop();
    }
    
    // if it's not empty then it's safe to peek
    public boolean isEmpty() {
        return instance.get().isEmpty();
    }
    
    public T peek() {
        return instance.get().peek();
    }
}
