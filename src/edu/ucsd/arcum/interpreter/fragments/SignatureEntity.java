package edu.ucsd.arcum.interpreter.fragments;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;
import static edu.ucsd.arcum.interpreter.query.Entity.getDisplayString;

import java.util.List;

import org.eclipse.jdt.core.dom.*;

import edu.ucsd.arcum.interpreter.query.Entity;

// A signature is a method without the body. It can be coerced to either an interface
// abstract method declaration or to a method with a default body generated.
public class SignatureEntity implements ISynthesizedEntity
{
    private MethodDeclaration methodDecl;

    public SignatureEntity(MethodDeclaration methodDecl) {
        this.methodDecl = methodDecl;
    }

    // The MethodDeclaration returned might not have an empty body
    public MethodDeclaration getSignatureNode() {
        return methodDecl;
    }

    @Override public String toString() {
        return Entity.getDisplayString(methodDecl);
    }

    // Compare node structure, ignoring the body
    @Override public boolean equals(Object obj) {
        if (obj == null || SignatureEntity.class != obj.getClass()) {
            return false;
        }
        else {
            SignatureEntity that = (SignatureEntity)obj;
            AST ast = AST.newAST(AST.JLS3);
            MethodDeclaration decl1 = (MethodDeclaration)Entity.copySubtree(ast, this.methodDecl);
            MethodDeclaration decl2 = (MethodDeclaration)Entity.copySubtree(ast, that.methodDecl);
            decl1.setBody(null);
            decl2.setBody(null);
            return Entity.compareTo(decl1, decl2) == 0;
        }
    }

    public boolean hasSameSignatureAs(IMethodBinding thatMethod) {
        String thisMethodName = methodDecl.getName().getIdentifier();
        String thatMethodName = thatMethod.getName();
        if (!thisMethodName.equals(thatMethodName))
            return false;
        // XXX (!!!) - Broken, only does name and num args comparison: should do better
        // type comparison and look at the return type, and potentially all flags too:
        // One issue is what to do with covariant return types. Need to decide if
        // parameter names are important too... information we might not be able to get
        List thisParameters = methodDecl.parameters();
        ITypeBinding[] thatParameters = thatMethod.getParameterTypes();
        if (thisParameters.size() != thatParameters.length)
            return false;
        for (int i = 0; i < thisParameters.size(); ++i) {
            SingleVariableDeclaration thisParam;
            thisParam = (SingleVariableDeclaration)thisParameters.get(i);
            Type thisParamType = thisParam.getType();
            ITypeBinding thatParamType = thatParameters[i];
            boolean sameType = (Entity.compareTo(thisParamType, thatParamType) == 0);
            if (!sameType) {
                return false;
            }
            if (DEBUG) {
                System.err.printf("Does %s match %s? %b%n",
                    getDisplayString(thisParamType), getDisplayString(thatParamType),
                    sameType);
            }
        }
        return true;
    }
}
