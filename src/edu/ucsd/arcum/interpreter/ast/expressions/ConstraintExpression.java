package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.query.OptionMatchTable;
import edu.ucsd.arcum.interpreter.satisfier.Satisfier;

public abstract class ConstraintExpression
{
    private SourceLocation position;

    protected ConstraintExpression(SourceLocation position) {
        this.position = position;
    }

    public abstract Set<String> getArcumVariableReferences();

    public abstract String toString();

    public final void checkUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        doCheckUserDefinedPredicates(tupleSets, varsInScope);
    }

    protected abstract void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope);

    // TODO: Complete this implementation
    public void visitSubExpressions(ISubExpressionVisitor visitor) {}

    public abstract Set<String> findAllTraitDependencies();

    public abstract Set<String> findNonMonotonicDependencies();

    public SourceLocation getPosition() {
        return position;
    }

    protected void extendPosition(SourceLocation end) {
        this.position = position.extendedTo(end);
    }

    public final boolean evaluate(IEntityLookup lookup, EntityDataBase edb,
        OptionMatchTable symTab)
    {
        Satisfier sat = new Satisfier(this);
        boolean result = sat.evaluate(lookup, edb, symTab);
        return result;
    }

    protected Set<String> flattenFindAllTraitDependencies(
        List<? extends ConstraintExpression>... listsOfExprs)
    {
        Set<String> result = Sets.newHashSet();
        for (List<? extends ConstraintExpression> listOfExprs : listsOfExprs) {
            for (ConstraintExpression expr : listOfExprs) {
                result.addAll(expr.findAllTraitDependencies());
            }
        }
        return result;
    }

    protected Set<String> flattenFindNonMonotonicDependencies(
        List<? extends ConstraintExpression>... listsOfExprs)
    {
        Set<String> result = Sets.newHashSet();
        for (List<? extends ConstraintExpression> listOfExprs : listsOfExprs) {
            for (ConstraintExpression expr : listOfExprs) {
                result.addAll(expr.findNonMonotonicDependencies());
            }
        }
        return result;
    }
}