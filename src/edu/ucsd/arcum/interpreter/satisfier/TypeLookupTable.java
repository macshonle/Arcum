package edu.ucsd.arcum.interpreter.satisfier;

import static com.google.common.collect.Lists.transform;

import java.util.*;
import java.util.Map.Entry;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.query.TraitValue;

public class TypeLookupTable
{
    final Map<String, EntityType> lookup;

    public TypeLookupTable(List<TraitSignature> tscs) {
        this.lookup = new HashMap<String, EntityType>();
        for (TraitSignature traitSignature : tscs) {
            List<FormalParameter> formals = traitSignature.getFormals();
            addFormals(formals);
        }
    }

    public void addFormals(List<FormalParameter> formals) {
        for (FormalParameter formal : formals) {
            String id = formal.getIdentifier();
            EntityType type = formal.getType();
            addType(id, type);
        }
    }

    public EntityType addType(String id, EntityType type) {
        return lookup.put(id, type);
    }

    public TypeLookupTable(Collection<TraitValue> tupleSetActuals) {
        this(transform(new ArrayList<TraitValue>(tupleSetActuals),
            new Function<TraitValue, TraitSignature>() {
                public TraitSignature apply(TraitValue from) {
                    return from.getTraitType();
                }
            }));
    }

    public EntityType lookupType(String id) {
        if (id.equals(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE)) {
            return EntityType.ANY;
        }
        return lookup.get(id);
    }

    public boolean hasInformationFor(String varName) {
        return lookup.containsKey(varName);
    }

    public void removeInformationFor(String varName) {
        lookup.remove(varName);
    }
    
    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("{");
        Set<Entry<String, EntityType>> entrySet = this.lookup.entrySet();
        Iterator<Entry<String, EntityType>> it = entrySet.iterator();
        while (it.hasNext()) {
            Entry<String, EntityType> entry = it.next();
            buff.append(entry.getKey());
            buff.append(" = ");
            buff.append(entry.getValue());
            if (it.hasNext()) {
                buff.append(String.format("%n"));
            }
        }
        buff.append("}");
        return buff.toString();
    }

    public static TypeLookupTable newScope(TypeLookupTable types,
        List<FormalParameter> boundVars)
    {
        List<TraitSignature> empty = Lists.newArrayList();
        TypeLookupTable result = new TypeLookupTable(empty);
        result.lookup.putAll(types.lookup);
        result.addFormals(boundVars);
        return result;
    }

    public static TypeLookupTable newScope(TypeLookupTable types, FormalParameter boundVar)
    {
        return newScope(types, Lists.newArrayList(boundVar));
    }
}
