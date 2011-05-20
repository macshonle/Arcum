package edu.ucsd.arcum.interpreter.ast;

import java.util.List;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;

// define properAccess(Expr use, Expr reference) with
//   use == ([`reference.put(`_, `_)] || [`reference.get(`_)]) &&
//   reference == [`targetType.`mapField];
public class StaticRealizationStatement extends RealizationStatement
{
    private boolean isNested = false;
    private boolean isLocal = false;

    public static StaticRealizationStatement makeLocal(RealizationStatement stmt,
        TraitSignature signature)
    {
        StaticRealizationStatement result = new StaticRealizationStatement(
            stmt.declaration, signature.getName(), stmt.getExpression(), null, stmt
                .getPosition());
        result.isLocal = true;
        result.addTraitSignature(signature);
        return result;
    }

    public static StaticRealizationStatement makeStatic(TopLevelConstruct declaration,
        String name, ConstraintExpression patternExpr, List<FormalParameter> params,
        SourceLocation location)
    {
        StaticRealizationStatement result = new StaticRealizationStatement(declaration,
            name, patternExpr, null, location);
        TraitSignature signature = TraitSignature.makeStaticDefinition(name, params);
        result.addTraitSignature(signature);
        return result;
    }

    public static StaticRealizationStatement makeNested(TopLevelConstruct declaration,
        String name, ConstraintExpression patternExpr, List<FormalParameter> params,
        SourceLocation location)
    {
        StaticRealizationStatement result;
        result = makeStatic(declaration, name, patternExpr, params, location);
        result.isNested = true;
        return result;
    }

    private StaticRealizationStatement(TopLevelConstruct declaration, String name,
        ConstraintExpression bodyExpr, List<FormalParameter> formals,
        SourceLocation location)
    {
        super(declaration, location);
        setBodyExpression(bodyExpr);
    }

    @Override public void typeCheckAndValidate(OptionInterface optionInterface) {}

    @Override public String toString() {
        return super.toString().replaceFirst("realize", "define");
    }

    @Override public void verifyValidVariables() {
    // MACNEIL: probably need to do something now, and this comment might or
    // might not be correct
    // We don't need to do anything if we aren't local, because then we don't
    // need to generate code and thus the "any" variable is acceptable 
    }

    @Override public boolean isLocal() {
        return isLocal;
    }

    @Override protected boolean isNested() {
        return isNested;
    }

    @Override public boolean isStatic() {
        return true;
    }

    @Override protected boolean excludesUseOfAnyVariable() {
        return isLocal();
    }
}