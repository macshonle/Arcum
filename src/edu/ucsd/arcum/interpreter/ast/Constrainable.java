package edu.ucsd.arcum.interpreter.ast;

import java.util.List;

import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;

public interface Constrainable
{
    List<ConstraintExpression> getRequireClauses();
    
    List<ErrorMessage> getErrorMessages();
    
    // pass in EMPTY_MESSAGE if no message was present
    void addRequiresClause(ConstraintExpression condition, ErrorMessage message);
}
