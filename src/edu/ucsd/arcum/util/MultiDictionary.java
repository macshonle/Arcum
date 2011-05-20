package edu.ucsd.arcum.util;

import java.util.*;

// A dictionary where each key can have several values
public class MultiDictionary<K, V> implements Iterable<Map.Entry<K, List<V>>>
{
    private LinkedHashMap<K, List<V>> map;

    public static <K, V> MultiDictionary<K, V> newInstance() {
        return new MultiDictionary<K, V>();
    }
    
    private MultiDictionary() {
        this.map = new LinkedHashMap<K, List<V>>();
    }
    
    public void addDefinition(K key, V value) {
        List<V> defs = map.get(key);
        if (defs == null) {
            defs = new ArrayList<V>();
            map.put(key, defs);
        }
        defs.add(value);
    }
    
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }
    
    @Override
    public String toString() {
        StringBuilder buff = new StringBuilder();
        for (Map.Entry<K,List<V>> entry: map.entrySet()) {
            buff.append(entry.getKey());
            buff.append(String.format(" ->%n["));
            buff.append(StringUtil.separate(entry.getValue()));
            buff.append(String.format("]%n"));
        }
        return buff.toString();
    }

    public Iterator<Map.Entry<K, List<V>>> iterator() {
        return map.entrySet().iterator();
    }

    public void putAll(K key, List<V> values) {
        for (V value: values) {
            addDefinition(key, value);
        }
    }

    public int numberOfKeys() {
        return map.size();
    }
}