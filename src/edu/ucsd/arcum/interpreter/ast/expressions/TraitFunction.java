package edu.ucsd.arcum.interpreter.ast.expressions;

import static edu.ucsd.arcum.interpreter.query.EntityDataBase.BUILT_IN_TRAIT_TYPES;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.EntityTuple;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.query.TraitValue;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.satisfier.BindingsSet;

public class TraitFunction implements IFunction
{
    private String traitName;

    public TraitFunction(String traitName) {
        this.traitName = traitName;
    }

    // An arg in the "args" list is either a program entity, the special
    // Entity.ANY_ENTITY, or a NameReference
    @Override public BindingsSet evaluate(List<Object> args, IEntityLookup entityLookup,
        BindingMap theta, boolean matchingMode, SourceLocation location)
    {
        BindingsSet result;
        if (!matchingMode && BUILT_IN_TRAIT_TYPES.containsKey(traitName)) {
            // In generation mode (i.e., not matching mode) we assert that all
            // built-in traits are true. Note that the non-built-in traits are
            // looked up even in generation mode.
            result = BindingsSet.newEmptySet();
            BindingMap assertedEntry = BindingMap.newEmptyMap();
            TraitSignature type = BUILT_IN_TRAIT_TYPES.get(traitName);
            List<FormalParameter> formals = type.getFormals();
            TraitValue tuples = new TraitValue(traitName, type);
            Map<String, Object> values = Maps.newHashMap();
            for (int i = 0; i < args.size(); ++i) {
                String paramName = formals.get(i).getIdentifier();
                values.put(paramName, args.get(i));
            }
            EntityTuple tuple = new EntityTuple(type, values, null);
            tuples.addTuple(tuple);
            assertedEntry.bind(traitName, tuples, EntityType.TRAIT);
            assertedEntry.addBindings(theta);
            result.addEntry(assertedEntry);
        }
        else {
            Object lookup = entityLookup.lookupEntity(traitName);
            TraitValue traitValue = (TraitValue)lookup;
            result = traitValue.getMatches(args, theta);
        }
        return result;
    }

    @Override public List<EntityType> checkArgs(SourceLocation location,
        List<TraitSignature> tupleSets, int numGiven)
    {
        boolean isInList = false;
        List<FormalParameter> formals = null;
        checking: for (TraitSignature declaration : tupleSets) {
            if (declaration.getName().equals(traitName)) {
                int numExpectedArgs = declaration.getNumberOfParameters();
                if (numExpectedArgs == numGiven) {
                    isInList = true;
                }
                else {
                    ArcumError.fatalUserError(location,
                        "The predicate \"%s\" expects %d arguments, instead found %d",
                        traitName, numExpectedArgs, numGiven);
                }
                formals = declaration.getFormals();
                break checking;
            }
        }
        if (!isInList) {
            ArcumError.fatalUserError(location, "The predicate \"%s\" is undefined",
                traitName);
        }
        List<EntityType> result = Lists.transform(formals, FormalParameter.getType);
        return result;
    }

    @Override public String toString() {
        return traitName;
    }
    
    @Override public String getName() {
        return traitName;
    }
}