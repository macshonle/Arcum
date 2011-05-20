package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.util.StringUtil;

// NOTE: Rampant code duplication with UniversalQuantifier, not that it matter too
// much.
//@CopiedFrom(UniversalQuanitifier.class)
public class ExistentialQuantifier extends ConstraintExpression
{
    private List<FormalParameter> boundVars;
    private ConstraintExpression body;

    public ExistentialQuantifier(SourceLocation location,
        List<FormalParameter> boundVars, ConstraintExpression body)
    {
        super(location);
        this.boundVars = boundVars;
        this.body = body;
    }

    @Override
    protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        Set nextScope = Sets.newHashSet(varsInScope);
        nextScope.addAll(Lists.transform(boundVars, FormalParameter.getIdentifier));
        body.doCheckUserDefinedPredicates(tupleSets, nextScope);
    }

    @Override
    public Set<String> getArcumVariableReferences() {
        Set<String> result = Sets.newHashSet(body.getArcumVariableReferences());
        for (FormalParameter boundVar : boundVars) {
            result.remove(boundVar.getIdentifier());
        }
        result.remove(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("exists ");
        StringUtil.separate(buff, boundVars, ", ");
        buff.append(" { ");
        buff.append(body.toString());
        buff.append(" }");
        return buff.toString();
    }

    public List<FormalParameter> getBoundVars() {
        return boundVars;
    }

    public ConstraintExpression getBody() {
        return body;
    }

    @Override
    public Set<String> findAllTraitDependencies() {
        Set<String> result = body.findAllTraitDependencies();
        result.removeAll(Lists.transform(boundVars, FormalParameter.getIdentifier));
        return result;
    }

    // @Difference
    @Override
    public Set<String> findNonMonotonicDependencies() {
        Set<String> result = body.findNonMonotonicDependencies();
        result.removeAll(Lists.transform(boundVars, FormalParameter.getIdentifier));
        return result;
    }
}
