package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;

public class TrueLiteral extends ConstraintExpression
{
    public TrueLiteral(SourceLocation location) {
        super(location);
    }

    @Override public Set<String> getArcumVariableReferences() {
        return Collections.emptySet();
    }

    @Override public String toString() {
        return "true";
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets, Set<String> varsInScope) {
        // intentionally left blank
        ;
    }

    @Override public Set<String> findAllTraitDependencies() {
        return Sets.newHashSet();
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return Sets.newHashSet();
    }
}
