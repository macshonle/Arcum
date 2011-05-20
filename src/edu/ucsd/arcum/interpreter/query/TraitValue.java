package edu.ucsd.arcum.interpreter.query;

import static edu.ucsd.arcum.util.StringUtil.separate;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.satisfier.BindingsSet;

public class TraitValue
{
    private final String traitName;
    private final TraitSignature traitType;
    private final Set<EntityTuple> entities;
    private final boolean isStatic;
    private final boolean isNested;

    public TraitValue(String traitName, TraitSignature tupleSetType) {
        this(traitName, tupleSetType, false, false);
    }

    public TraitValue(String traitName, TraitSignature tupleSetType, boolean isStatic,
        boolean isNested)
    {
        this.traitName = traitName;
        this.traitType = tupleSetType;
        this.entities = Sets.newTreeSet();
        this.isStatic = isStatic;
        this.isNested = isNested;
    }

    public EntityTuple getSingleton() {
        if (entities.size() != 1) {
            ArcumError.fatalError("Non-singleton");
        }
        return entities.iterator().next();
    }

    public List<EntityTuple> getEntities() {
        return Lists.newArrayList(entities);
    }

    // Returns true if this set did not already contain the specified element 
    public boolean addTuple(EntityTuple entityTuple) {
        return entities.add(entityTuple);
    }

    @Override public String toString() {
        return String.format("{%s}", separate(entities, String.format("%n ")));
    }

    public String getTraitName() {
        return traitName;
    }

    public TraitSignature getTraitType() {
        return traitType;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isNested() {
        return isNested;
    }

    public Collection<String> getNamesOfDeclaredFormals() {
        return traitType.getNamesOfDeclaredFormals();
    }

    // An arg in the "args" list is either a program entity or a VariablePlaceholder.
    // Arguments are assumed to be in order.
    public BindingsSet getMatches(List<Object> args, BindingMap in) {
        List<FormalParameter> formals = traitType.getFormals();
        BindingsSet result = BindingsSet.newEmptySet();
        for (EntityTuple entity : entities) {
            BindingMap theta = entity.matches(formals, args);
            if (theta != null) {
                result.addEntry(theta);
            }
        }
        for (BindingMap bindingMap : result) {
            // We add these later so that addEntry doesn't have to compare as many
            // items
            bindingMap.addBindings(in);
        }
        return result;
    }
}