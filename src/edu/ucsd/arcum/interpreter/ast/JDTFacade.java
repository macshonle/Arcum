package edu.ucsd.arcum.interpreter.ast;

import java.util.List;

import org.eclipse.jdt.core.dom.*;

import edu.ucsd.arcum.interpreter.query.EntityDataBase;

public class JDTFacade
{
    public static ITypeBinding declaredClassOf(FieldDeclaration field) {
        ITypeBinding result = ASTUtil.queryNewParentOf(field);
        if (result == null) {
            // Otherwise, maybe it's already in the program
            TypeDeclaration owner = (TypeDeclaration)field.getParent();
            result = (ITypeBinding)EntityDataBase.resolveBinding(owner);
        }
        return result;
    }

    public static boolean declaresVariableNamed(FieldDeclaration field, String identifier) {
        List fragments = field.fragments();
        for (Object obj: fragments) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment)obj;
            if (fragment.getName().getIdentifier().equals(identifier)) {
                return true;
            }
        }
        return false;
    }
}
