package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.List;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.satisfier.BindingsSet;

public interface IFunction
{
    String getName();

    BindingsSet evaluate(List<Object> args, IEntityLookup entityLookup, BindingMap theta,
        boolean matchingMode, SourceLocation location);

    List<EntityType> checkArgs(SourceLocation location, List<TraitSignature> tupleSets,
        int numGiven);
}