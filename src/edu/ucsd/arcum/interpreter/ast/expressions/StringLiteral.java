package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.util.StringUtil;

public class StringLiteral extends ConstraintExpression
{
    private String text;

    public StringLiteral(SourceLocation location, String text) {
        super(location);
        this.text = text;
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets, Set<String> varsInScope) {
        // intentionally left blank
        ;
    }

    @Override public Set<String> getArcumVariableReferences() {
        return Collections.emptySet();
    }

    @Override public String toString() {
        return String.format("\"%s\"", StringUtil.escape(text));
    }

    public String getText() {
        return text;
    }

    @Override public Set<String> findAllTraitDependencies() {
        return Sets.newHashSet();
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return Sets.newHashSet();
    }
}
