package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;

public class FalseLiteral extends ConstraintExpression
{
    public FalseLiteral(SourceLocation location) {
        super(location);
    }

    @Override public String toString() {
        return "false";
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets, Set<String> varsInScope) {
    // intentionally left blank
    }

    @Override public Set<String> getArcumVariableReferences() {
        return Collections.EMPTY_SET;
    }

    // EXAMPLE: This used to return a List, but now returns a Set. Different semantics,
    // but you could imagine there being checks that remove isn't used and instead
    // removeAll is used.
    @Override public Set<String> findAllTraitDependencies() {
        return Sets.newHashSet();
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return Sets.newHashSet();
    }
}