package edu.ucsd.arcum.interpreter.ast;

import java.util.Set;

// A requirement not attached to a trait or any other realize clause. Typical
// examples would be forall statements
public class FreeStandingRequirements extends ArcumDeclarationType
{

    @Override protected Set<String> doGetVariablesInScope(Set<String> currentScope) {
        // We do not introduce any new variables, so we return the given set
        return currentScope;
    }
}
