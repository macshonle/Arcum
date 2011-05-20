package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;

public class SelectExpression extends ConstraintExpression
{
    private final List<ConstraintExpression> conditions;
    private final List<ConstraintExpression> values;

    public SelectExpression(SourceLocation location,
        List<ConstraintExpression> conditions, List<ConstraintExpression> values)
    {
        super(location);
        this.conditions = conditions;
        this.values = values;
        if (conditions.size() + 1 != values.size()) {
            ArcumError.fatalError("Internal parsing error: condition clause");
        }
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        for (ConstraintExpression condition : conditions) {
            condition.doCheckUserDefinedPredicates(tupleSets, varsInScope);
        }
        for (ConstraintExpression value : values) {
            value.doCheckUserDefinedPredicates(tupleSets, varsInScope);
        }
    }

    @Override public Set<String> getArcumVariableReferences() {
        Set<String> result = Sets.newHashSet();
        for (ConstraintExpression condition : conditions) {
            result.addAll(condition.getArcumVariableReferences());
        }
        result.remove(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE);
        for (ConstraintExpression value : values) {
            result.addAll(value.getArcumVariableReferences());
        }
        return result;
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append(String.format("select {%n"));
        for (int i = 0; i < conditions.size(); ++i) {
            buff.append(conditions.get(i).toString());
            buff.append(" : ");
            buff.append(values.get(i).toString());
            buff.append(String.format(",%n"));
        }
        buff.append("default : ");
        buff.append(values.get(values.size() - 1).toString());
        buff.append(String.format("%n"));
        buff.append("}");
        return buff.toString();
    }

    public List<ConstraintExpression> getConditions() {
        return conditions;
    }

    public List<ConstraintExpression> getValues() {
        return values;
    }

    // EXAMPLE: This could probably be accomplished with visitors or collectors
    @Override public Set<String> findAllTraitDependencies() {
        return flattenFindAllTraitDependencies(conditions, values);
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return flattenFindNonMonotonicDependencies(conditions, values);
    }
}