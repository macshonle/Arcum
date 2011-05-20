package edu.ucsd.arcum.interpreter.query;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;

import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.satisfier.TypeLookupTable;

public interface IEntityLookup
{
    Object lookupEntity(String reference);
    
    String lookupEntitiesID(Object entity);

    BindingMap extractAsBindings();

    FormalParameter findResolvedSingleton(String variableName);
    
    FragmentParser newParser(boolean matchingMode);
    
    AbstractTypeDeclaration lookupTypeDeclaration(ITypeBinding givenBinding);
    
    ITypeBinding lookupTypeBinding(ASTNode node);

    TypeLookupTable getTypeLookupTable();
}
