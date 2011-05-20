package edu.ucsd.arcum.interpreter.fragments;

import java.util.List;

import org.eclipse.jdt.core.dom.*;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.Unreachable;
import edu.ucsd.arcum.interpreter.ast.ASTUtil;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.transformation.Conversion;
import edu.ucsd.arcum.util.StringUtil;

public class DeclarationElement extends ProgramFragment
{
    private ITypeBinding typeBinding;
    private List<IExtendedModifier> modifiers;

    // XXX (!!!) -- This code does not support the "package" access idea well, leading
    // to some matches not being made.
    public DeclarationElement(ITypeBinding typeBinding, List<IExtendedModifier> modifiers) {
        this.typeBinding = typeBinding;
        this.modifiers = modifiers;
    }

    @Override
    protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append(typeBinding.getBinaryName());
    }

    @Override
    public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        ASTNodeReplacer declarationNodeReplacer = this.new DeclarationNodeReplacer(ast);
        BindingMap result = bindRoot(declarationNodeReplacer);
        return result;
    }

    @Override
    protected BindingMap matchesASTNode(ASTNode node) {
        ITypeBinding foundType;
        List foundModifiers;
        if (node instanceof Type && modifiers.isEmpty()) {
            foundModifiers = Lists.newArrayList();
        }
        else {
            foundModifiers = ASTUtil.getExtendedModifiers(node);
        }

        if (node instanceof Type) {
            // GETDONE: We really only want return types and casts, not everywhere
            // a Type would appear: Although maybe the database is only populated
            // with the right kind of Type instances in the first place.
            Type type = (Type)node;
            foundType = type.resolveBinding();
        }
        else if (node instanceof SingleVariableDeclaration
            || node instanceof VariableDeclarationFragment)
        {
            VariableDeclaration varDecl = (VariableDeclaration)node;
            foundType = varDecl.resolveBinding().getType();
        }
        else if (node instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varDeclStmt = (VariableDeclarationStatement)node;
            List frags = varDeclStmt.fragments();
            if (frags.size() != 1) {
                ArcumError.fatalError("Desugaring error: %s", //->
                    StringUtil.debugDisplay(node));
            }
            VariableDeclarationFragment frag = (VariableDeclarationFragment)frags.get(0);
            // GETDONE: and below
//            foundType = frag.resolveBinding().getType();
            foundType = ((IVariableBinding)EntityDataBase.resolveBinding(frag)).getType();
        }
        else if (node instanceof FieldDeclaration) {
            FieldDeclaration fieldDecl = (FieldDeclaration)node;
            List frags = fieldDecl.fragments();
            if (frags.size() != 1) {
                ArcumError.fatalError("Desugaring error: %s", //->
                    StringUtil.debugDisplay(node));
            }
            VariableDeclarationFragment frag = (VariableDeclarationFragment)frags.get(0);
            // GETDONE: What's the right thing we're looking for?
//            foundType = frag.resolveBinding().getType();
            foundType = ((IVariableBinding)EntityDataBase.resolveBinding(frag)).getType();
        }
        else {
            ArcumError.fatalError("Unhandled DeclarationElement case: %s", //->
                StringUtil.debugDisplay(node));
            return null;
        }
        boolean typesMatch = (Entity.compareTo(this.typeBinding, foundType) == 0);
        if (typesMatch) {
            boolean modifiersSubsetMatch = Entity.subsetOf(modifiers, foundModifiers);
            if (modifiersSubsetMatch) {
                BindingMap result = bindRoot(node);
                return result;
            }
        }
        return super.matchesASTNode(node);
    }

    private class DeclarationNodeReplacer implements ASTNodeReplacer
    {
        private AST ast;

        public DeclarationNodeReplacer(AST ast) {
            this.ast = ast;
        }

        @Override
        public ASTNode generateReplacement(ASTNode original) {
            Type typeNode = Conversion.typeBindingtoASTNode(ast, typeBinding);
            if (original instanceof Type) {
                // VERSION2: How do annotations get added to something like
                // List<@ReadOnly Map<String, String>>... answer: They can't! We
                // need to work around this (e.g. making wrappers) or see what
                // the next version of Java does.
                //
                // Wrappers: List<ReadOnlyMap<String, String>> ...
                // class ReadOnlyMap<T,U> { Map<T,U> baseMap }
                // where the wrapper is an automatically created delegator
                Type type = (Type)original;
                if (!modifiers.isEmpty()) {
                    StructuralPropertyDescriptor spd = type.getLocationInParent();
                    if (spd == MethodDeclaration.RETURN_TYPE2_PROPERTY) {
                    }
                    ArcumError.fatalError("How do annotations get added to this?");
                    throw new Unreachable();
                }
                return typeNode;
            }
            else if (original instanceof SingleVariableDeclaration) {
                SingleVariableDeclaration svd = (SingleVariableDeclaration)original;
                SingleVariableDeclaration result = Entity.copySubtree(ast, svd);
                result.setType(typeNode);
                insertModifiers(result.modifiers(), modifiers, ast);
                return result;
            }
            else if (original instanceof VariableDeclarationFragment) {
                // GETDONE: Handle this case and the similar one with field declarations
                // below: We need to change the sugared form, and separate it from
                // the original form (sometimes in three separate pieces to preserve
                // order). E.g.,
                //  Object a=f(), b[]=new Object[5], c=null;
                // becomes:
                //  Object a=f();
                //  Foo b[] = new Foo[5];
                //  Object c=null;
                // Also need to mind changes to the other elements, if they need to
                // be changed too. This is somewhat related to the problem of adding
                // an annotation to a method's modifiers list.
                VariableDeclarationFragment vdf = (VariableDeclarationFragment)original;
                ArcumError.fatalError("Unsupported case: Replacing %s", ASTUtil.getDebugString(vdf));
                throw new Unreachable();
            }
            else if (original instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement vds = (VariableDeclarationStatement)original;
                VariableDeclarationStatement result = Entity.copySubtree(ast, vds);
                result.setType(typeNode);
                insertModifiers(result.modifiers(), modifiers, ast);
                return result;
            }
            else if (original instanceof FieldDeclaration) {
                // GETDONE: handle case where a FieldDeclaration needs to be broken up
                FieldDeclaration fd = (FieldDeclaration)original;
                FieldDeclaration result = Entity.copySubtree(ast, fd);
                result.setType(typeNode);
                insertModifiers(result.modifiers(), modifiers, ast);
                return result;
            }
            else {
                ArcumError.fatalError("Unhandled case: %s", //->
                    ASTUtil.getDebugString(original));
                throw new Unreachable();
            }
        }

        @Override
        public String toString() {
            return typeBinding.getQualifiedName();
        }
    }
    
    private static void insertModifiers(List toAdd, List<IExtendedModifier> modifiers, AST ast) {
        for (IExtendedModifier modifier : modifiers) {
            ASTNode node;
            if (modifier.isAnnotation()) {
                node = (Annotation)modifier;
            }
            else if (modifier.isModifier()){
                node = (Modifier)modifier;
            }
            else {
                ArcumError.fatalError("Impossible condition");
                throw new Unreachable();
            }
            ASTNode copy = Entity.copySubtree(ast, node);
            toAdd.add(copy);
        }
    }
}