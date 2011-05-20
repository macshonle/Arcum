package edu.ucsd.arcum.interpreter.transformation;

import org.eclipse.jdt.core.dom.*;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.ASTUtil;
import edu.ucsd.arcum.interpreter.fragments.ModifierElement;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.util.StringUtil;

public class Conversion
{
    public static <T extends ASTNode> T cleanseASTNode(AST ast, T node) {
        T result = node;
        if (ast != result.getAST() || result.getParent() != null) {
            result = Entity.copySubtree(ast, result);
        }
        return result;
    }

    public static Type typeBindingtoASTNode(AST ast, ITypeBinding typeBinding) {
        Type result = ASTUtil.buildTypeNode(ast, typeBinding);
        return cleanseASTNode(ast, result);
    }

    // Returns null if the element itself exists only implicitly, like the "package"
    // modifier Arcum uses in the absence of public/private/protected.
    public static ASTNode toPossibleEmptyNode(AST ast, StructuralPropertyDescriptor edge,
        Object value)
    {
        if (value instanceof ASTNode) {
            return cleanseASTNode(ast, (ASTNode)value);
        }
        else if (value instanceof ITypeBinding) {
            return typeBindingtoASTNode(ast, (ITypeBinding)value);
        }
        else if (value instanceof ModifierElement) {
            return modifierToASTNode(ast, (ModifierElement)value);
        }
        else if (value instanceof String){
            if (edge.isChildProperty()) {
                ChildPropertyDescriptor cpd = (ChildPropertyDescriptor)edge;
                Class childType = cpd.getChildType();
                if (childType == SimpleName.class) {
                    return ast.newSimpleName((String)value);
                }
            }
            ArcumError.fatalError("Unhandled case: %s%n", StringUtil.debugDisplay(value));
            return null;
        }
        else {
            ArcumError.fatalError("Unhandled case: %s%n", StringUtil.debugDisplay(value));
            return null;
        }
    }

    private static ASTNode modifierToASTNode(AST ast, ModifierElement modifierElement) {
        if (modifierElement == ModifierElement.MOD_PACKAGE) {
            return null;
        }
        else {
            Modifier result = modifierElement.asModifierASTNode(ast);
            return cleanseASTNode(ast, result);
        }
    }

    public static Object toSimpleProperty(Object value) {
        if (value instanceof SimpleName) {
            SimpleName name = (SimpleName)value;
            return name.getIdentifier();
        }
        return value;
    }

    public static ASTNode unbox(AST ast, StructuralPropertyDescriptor property,
        ASTNode node)
    {
        if (property.isChildProperty()) {
            ChildPropertyDescriptor astProperty = (ChildPropertyDescriptor)property;
            Class childType = astProperty.getChildType();
            if (childType.isAssignableFrom(node.getClass())) {
                // no conversion needed
                return node;
            }
            else {
                if (node instanceof SimpleType && childType.isAssignableFrom(Name.class))
                {
                    SimpleType simpleType = (SimpleType)node;
                    Name name = simpleType.getName();
                    // unparent "name"
                    return Entity.copySubtree(ast, name);
                }
                else if (node instanceof ParameterizedType
                    && childType.isAssignableFrom(Name.class))
                {
                    ParameterizedType parameterizedType = (ParameterizedType)node;
                    Type baseType = parameterizedType.getType();
                    if (baseType instanceof SimpleType) {
                        SimpleType simpleType = (SimpleType)baseType;
                        Name name = simpleType.getName();
                        // unparent "name"
                        return Entity.copySubtree(ast, name);
                    }
                    else {
                        ArcumError.fatalError("Conversion ase not supported yet for: %s",
                            baseType);
                    }
                }
                return node;
            }
        }
        // otherwise, probably no conversion is needed
        return node;
    }
}