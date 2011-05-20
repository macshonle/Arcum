package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.Set;

import edu.ucsd.arcum.exceptions.SourceLocation;

public class BooleanConjunction extends VariadicOperator
{
    public BooleanConjunction(SourceLocation location) {
        super(location);
    }

    public static BooleanConjunction conjoin(ConstraintExpression lhs,
        ConstraintExpression rhs)
    {
        SourceLocation lhsPos = lhs.getPosition();
        SourceLocation rhsPos = rhs.getPosition();
        SourceLocation location;
        if (lhsPos.isGenerated()) {
            location = rhsPos;
        }
        else {
            location = lhsPos.extendedTo(rhsPos);
        }
        BooleanConjunction result = new BooleanConjunction(location);
        result.addClause(lhs);
        result.addClause(rhs);
        return result;
    }

    @Override
    public String getOperatorLexeme() {
        return "&&\n   ";
    }

    @Override
    public Set<String> findAllTraitDependencies() {
        return flattenFindAllTraitDependencies(this.clauses);
    }

    @Override
    public Set<String> findNonMonotonicDependencies() {
        return flattenFindNonMonotonicDependencies(this.clauses);
    }
}