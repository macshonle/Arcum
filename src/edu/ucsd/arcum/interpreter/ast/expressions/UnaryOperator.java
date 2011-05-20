package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.List;
import java.util.Set;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;

public abstract class UnaryOperator extends ConstraintExpression
{
    protected ConstraintExpression operand;

    protected UnaryOperator(SourceLocation location, ConstraintExpression operand) {
        super(location);
        this.operand = operand;
    }

    @Override public Set<String> getArcumVariableReferences() {
        return operand.getArcumVariableReferences();
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        operand.doCheckUserDefinedPredicates(tupleSets, varsInScope);
    }
}
