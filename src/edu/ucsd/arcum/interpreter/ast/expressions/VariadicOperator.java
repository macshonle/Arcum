package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.*;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.util.StringUtil;

public abstract class VariadicOperator extends ConstraintExpression implements
    Iterable<ConstraintExpression>
{
    protected List<ConstraintExpression> clauses;

    protected VariadicOperator(SourceLocation location) {
        super(location);
        this.clauses = new ArrayList<ConstraintExpression>();
    }

    public void addClause(ConstraintExpression clause) {
        clauses.add(clause);
        extendPosition(clause.getPosition());
    }

    public Iterator<ConstraintExpression> iterator() {
        return clauses.iterator();
    }

    public Collection<ConstraintExpression> getClauses() {
        return clauses;
    }

    public int size() {
        return clauses.size();
    }

    public abstract String getOperatorLexeme();

    @Override public Set<String> getArcumVariableReferences() {
        Set<String> result = new HashSet<String>();
        for (ConstraintExpression clause : clauses) {
            result.addAll(clause.getArcumVariableReferences());
        }
        return result;
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        for (ConstraintExpression clause : clauses) {
            clause.doCheckUserDefinedPredicates(tupleSets, varsInScope);
        }
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("((");
        StringUtil.separate(buff, clauses, ") " + getOperatorLexeme() + " (");
        buff.append("))");
        return buff.toString();
    }
}