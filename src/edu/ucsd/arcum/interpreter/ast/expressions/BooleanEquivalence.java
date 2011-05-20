package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.Set;

import edu.ucsd.arcum.exceptions.SourceLocation;

public class BooleanEquivalence extends VariadicOperator
{
    public BooleanEquivalence(SourceLocation location) {
        super(location);
    }

    @Override public String getOperatorLexeme() {
        return "<=>";
    }

    @Override public Set<String> findAllTraitDependencies() {
        return flattenFindAllTraitDependencies(this.clauses);
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return this.findAllTraitDependencies();
    }
}
