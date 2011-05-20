package edu.ucsd.arcum.interpreter.ast.expressions;

public interface ISubExpressionVisitor
{
    public void visit(ConstraintExpression constraintExpression);
}
