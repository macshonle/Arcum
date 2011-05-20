package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.Set;

import edu.ucsd.arcum.exceptions.SourceLocation;

public class BooleanNegation extends UnaryOperator
{
    public BooleanNegation(SourceLocation location, ConstraintExpression condition) {
        super(location, condition);
    }
    
    @Override
    public String toString() {
        return "!(" + operand.toString() + ")";
    }
    
    public ConstraintExpression getOperand() {
        return operand;
    }

    @Override public Set<String> findAllTraitDependencies() {
         return operand.findAllTraitDependencies();
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return this.findAllTraitDependencies();
    }
}
