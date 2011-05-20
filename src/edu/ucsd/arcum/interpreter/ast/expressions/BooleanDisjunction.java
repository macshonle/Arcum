package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.Set;

import edu.ucsd.arcum.exceptions.SourceLocation;

public class BooleanDisjunction extends VariadicOperator
{
    public BooleanDisjunction(SourceLocation location) {
        super(location);
    }

    @Override public String getOperatorLexeme() {
        return "||\n   ";
    }

    @Override public Set<String> findAllTraitDependencies() {
        return flattenFindAllTraitDependencies(this.clauses);
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return flattenFindNonMonotonicDependencies(this.clauses);
    }
}
