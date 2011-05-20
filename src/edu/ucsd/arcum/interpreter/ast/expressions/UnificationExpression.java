package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;

public class UnificationExpression extends ConstraintExpression
{
    private String name;
    private ConstraintExpression expression;

    public UnificationExpression(SourceLocation position, String name,
        ConstraintExpression expression)
    {
        super(position);
        this.name = name;
        this.expression = expression;
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        expression.doCheckUserDefinedPredicates(tupleSets, varsInScope);
    }

    @Override public Set<String> getArcumVariableReferences() {
        Set<String> result = Sets.newHashSet(name);
        result.addAll(expression.getArcumVariableReferences());
        return result;
    }

    @Override public String toString() {
        return String.format("%s == (%s)", name, expression);
    }

    public String getName() {
        return name;
    }

    public ConstraintExpression getRightHandSide() {
        return expression;
    }

    @Override public Set<String> findAllTraitDependencies() {
        return expression.findAllTraitDependencies();
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return expression.findNonMonotonicDependencies();
    }
}