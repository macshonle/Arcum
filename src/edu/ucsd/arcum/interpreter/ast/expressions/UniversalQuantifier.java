package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.ErrorMessage;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.util.StringUtil;

public class UniversalQuantifier extends ConstraintExpression
{
    private List<FormalParameter> boundVars;
    private ConstraintExpression initialSet;
    private ConstraintExpression body;
    private ErrorMessage optionalMsg;

    public UniversalQuantifier(SourceLocation location, List<FormalParameter> boundVars,
        ConstraintExpression initialSet, ConstraintExpression body,
        ErrorMessage optionalMsg)
    {
        super(location);
        this.boundVars = boundVars;
        this.initialSet = initialSet;
        this.body = body;
        this.optionalMsg = optionalMsg;
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        Set nextScope = Sets.newHashSet(varsInScope);
        nextScope.addAll(Lists.transform(boundVars, FormalParameter.getIdentifier));
        initialSet.doCheckUserDefinedPredicates(tupleSets, nextScope);
        body.doCheckUserDefinedPredicates(tupleSets, nextScope);
    }

    @Override public Set<String> getArcumVariableReferences() {
        Set<String> result = Sets.newHashSet();
        result.addAll(initialSet.getArcumVariableReferences());
        Set<String> bodyVars = Sets.newHashSet(body.getArcumVariableReferences());
        for (FormalParameter boundVar : boundVars) {
            bodyVars.remove(boundVar.getIdentifier());
        }
        result.addAll(bodyVars);
        return result;
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("forall(");
        StringUtil.separate(buff, boundVars, ", ");
        buff.append(" : ");
        buff.append(initialSet.toString());
        buff.append(") {");
        buff.append(body.toString());
        buff.append(" }");
        return buff.toString();
    }

    public List<FormalParameter> getBoundVars() {
        return boundVars;
    }

    public ConstraintExpression getInitialSet() {
        return initialSet;
    }

    public ConstraintExpression getBody() {
        return body;
    }

    public boolean hasErrorMessage() {
        return optionalMsg != ErrorMessage.EMPTY_MESSAGE;
    }

    // call only if hasErrorMessage returns true
    public ErrorMessage getErrorMessage() {
        return optionalMsg;
    }

    @Override public Set<String> findAllTraitDependencies() {
        Set<String> result = body.findAllTraitDependencies();
        result.removeAll(Lists.transform(boundVars, FormalParameter.getIdentifier));
        result.addAll(initialSet.findAllTraitDependencies());
        return result;
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return this.findAllTraitDependencies();
    }
}