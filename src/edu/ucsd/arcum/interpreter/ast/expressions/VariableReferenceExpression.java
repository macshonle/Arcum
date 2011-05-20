package edu.ucsd.arcum.interpreter.ast.expressions;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;

public class VariableReferenceExpression extends ConstraintExpression
{
    private String name;

    public VariableReferenceExpression(SourceLocation position, String name) {
        super(position);
        this.name = name;
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        if (!varsInScope.contains(name)) {
            ArcumError.fatalUserError(getPosition(),
                "Reference to undefined variable %s (check spelling or scope)", name);
        }
    }

    @Override public Set<String> getArcumVariableReferences() {
        return Sets.newHashSet(name);
    }

    @Override public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public boolean isSpecialAnyVariable() {
        return name.equals(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE);
    }

    @Override public Set<String> findAllTraitDependencies() {
        return Sets.newHashSet();
    }

    @Override public Set<String> findNonMonotonicDependencies() {
        return Sets.newHashSet();
    }
}