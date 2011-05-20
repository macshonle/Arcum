package edu.ucsd.arcum.interpreter.fragments;

import java.util.Set;

import org.eclipse.jdt.core.dom.*;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.transformation.Conversion;

public class ResolvedType extends ProgramFragment
{
    private static Set<String> qualifiedNamesGenerated = Sets.newConcurrentHashSet();
    private ITypeBinding typeBinding;

    public ResolvedType(ITypeBinding typeBinding) {
        this.typeBinding = typeBinding;
    }
    
    public ITypeBinding getTypeBinding() {
        return typeBinding;
    }

    @Override protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append(typeBinding.getBinaryName());
    }

    @Override protected BindingMap matchesASTNode(ASTNode astNode) {
        if (astNode instanceof Name || astNode instanceof Type) {
            boolean matches = (Entity.compareTo(this.typeBinding, astNode) == 0);
            if (matches) {
                BindingMap result = bindRoot(astNode);
                return result;
            }
            else {
                return super.matchesASTNode(astNode);
            }
        }
        else {
            return super.matchesASTNode(astNode);
        }
    }

    @Override protected BindingMap matchesTypeBinding(ITypeBinding tb2) {
        boolean matches = (Entity.compareTo(this.typeBinding, tb2) == 0);
        if (matches) {
            BindingMap result = bindRoot(tb2);
            return result;
        }
        else {
            return null;
        }
    }

    @Override public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        ASTNode typeNode = Conversion.typeBindingtoASTNode(ast, typeBinding);
        String qualifiedName = typeBinding.getQualifiedName();
        qualifiedNamesGenerated.add(qualifiedName);
        return bindRoot(typeNode);
    }
    
    @Override public boolean isResolved() {
        return true;
    }
    
    @Override public BindingMap matchResolvedEntity() {
        BindingMap result = bindRoot(typeBinding);
        return result;
    }

    public static boolean isKnownType(String fullyQualifiedName) {
        return qualifiedNamesGenerated.contains(fullyQualifiedName);
    }
}