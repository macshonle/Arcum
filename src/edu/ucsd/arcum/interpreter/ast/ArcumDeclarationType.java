package edu.ucsd.arcum.interpreter.ast;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;

public abstract class ArcumDeclarationType implements Constrainable
{
    private List<ConstraintExpression> conditions;
    private List<ErrorMessage> messages;

    public ArcumDeclarationType() {
        this.conditions = Lists.newArrayList();
        this.messages = Lists.newArrayList();
    }

    public ArcumDeclarationType(Collection<ConstraintExpression> conditions,
        Collection<ErrorMessage> messages)
    {
        this.conditions = Lists.newArrayList(conditions);
        this.messages = Lists.newArrayList(messages);
    }

    public List<ConstraintExpression> getRequireClauses() {
        return conditions;
    }

    public List<ErrorMessage> getErrorMessages() {
        return messages;
    }

    public void addRequiresClause(ConstraintExpression condition, ErrorMessage message) {
        // we can inherit requirements from our parent, and the conditions list
        // has conjunction semantics, so this is what we want
        conditions.add(condition);
        messages.add(message);
    }

    public void checkUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        Set<String> nextScope = doGetVariablesInScope(varsInScope);
        for (ConstraintExpression requireClause : conditions) {
            requireClause.checkUserDefinedPredicates(tupleSets, nextScope);
        }
        for (ErrorMessage message : messages) {
            message.checkUserDefinedPredicates(tupleSets, nextScope);
        }
    }

    protected abstract Set<String> doGetVariablesInScope(Set<String> currentScope);
}