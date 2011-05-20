package edu.ucsd.arcum.interpreter.query;

import java.util.Map;

import org.eclipse.jdt.core.dom.IBinding;

import com.google.common.collect.Maps;

import edu.ucsd.arcum.exceptions.ArcumError;

// All IBinding instances define a getKey method that returns a unique key that
// represents that particular binding. Instances of this class serve as a wrapper
// around that String value.
public final class BindingKeyValue
{
    private static final Map<String, BindingKeyValue> cache = Maps.newHashMap();
    private static final Map<String, IBinding> lookup = Maps.newHashMap();
    private final EntityType type;
    private final String key;

    public static BindingKeyValue newInstance(EntityType type, IBinding binding) {
        String key = binding.getKey();
        BindingKeyValue result = cache.get(key);
        if (result == null) {
            result = new BindingKeyValue(type, key);
            cache.put(key, result);
            lookup.put(key, binding);
        }
        else {
            if (result.type != type) {
                ArcumError.fatalError("Internal error detected in BindingKeyValue.newInstance");
            }
        }
        return result;
    }

    public IBinding getOriginalBinding() {
        return lookup.get(key);
    }

    private BindingKeyValue(EntityType type, String key) {
        this.type = type;
        this.key = key;
    }

    public EntityType getType() {
        return type;
    }

    public String getKey() {
        return key;
    }
    
    @Override public int hashCode() {
        return key.hashCode();
    }
    
    @Override public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        BindingKeyValue that = (BindingKeyValue)obj;
        return this.key.equals(that.key);
    }
    
    @Override public String toString() {
        return this.key;
    }
}