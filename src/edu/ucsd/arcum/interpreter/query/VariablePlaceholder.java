package edu.ucsd.arcum.interpreter.query;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.expressions.VariableReferenceExpression;

public class VariablePlaceholder
{
    private final SourceLocation position;
    private final String name;
    private final EntityType type;

    public VariablePlaceholder(SourceLocation position) {
        this.position = position;
        this.name = ArcumDeclarationTable.SPECIAL_ANY_VARIABLE;
        this.type = EntityType.ANY;
    }

    public VariablePlaceholder(VariableReferenceExpression refExpr, EntityType type)
    {
        this.position = refExpr.getPosition();
        this.name = refExpr.getName();
        this.type = type;
    }

    public boolean isSpecialAnyVariable() {
        return type == EntityType.ANY;
    }

    public String getName() {
        return name;
    }

    public EntityType getType() {
        return type;
    }
    
    @Override
    public String toString() {
        return String.format("VariablePlaceholder(name=%s, type=%s, position=%s",
            name, type, position);
    }
}
