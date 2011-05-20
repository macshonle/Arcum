package edu.ucsd.arcum.interpreter.satisfier;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;

import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;

// Exports the same bindings found in the given base, but excluding the variables
// passed into the constructors exclusion set.
public class MaskedLookup implements IEntityLookup
{
    private IEntityLookup base;
    private Set<String> varsToExclude;

    public MaskedLookup(IEntityLookup base, Set<String> varsToExclude) {
        this.base = base;
        this.varsToExclude = varsToExclude;
    }

    @Override public BindingMap extractAsBindings() {
        BindingMap result = base.extractAsBindings();
        result = result.withVarsRemoved(varsToExclude);
        return result;
    }

    @Override public FormalParameter findResolvedSingleton(String variableName) {
        if (varsToExclude.contains(variableName)) {
            return null;
        }
        else {
            return base.findResolvedSingleton(variableName);
        }
    }

    @Override public String lookupEntitiesID(Object entity) {
        return base.lookupEntitiesID(entity);
    }

    @Override public Object lookupEntity(String reference) {
        if (varsToExclude.contains(reference)) {
            return null;
        }
        else {
            return base.lookupEntity(reference);
        }
    }

    @Override public ITypeBinding lookupTypeBinding(ASTNode node) {
        return base.lookupTypeBinding(node);
    }

    @Override public AbstractTypeDeclaration lookupTypeDeclaration(
        ITypeBinding givenBinding)
    {
        return base.lookupTypeDeclaration(givenBinding);
    }

    @Override public FragmentParser newParser(boolean matchingMode) {
        return base.newParser(matchingMode);
    }

    @Override
    public TypeLookupTable getTypeLookupTable() {
        TypeLookupTable result = base.getTypeLookupTable();
        for (String var : varsToExclude) {
            result.removeInformationFor(var);
        }
        return result;
    }

}
