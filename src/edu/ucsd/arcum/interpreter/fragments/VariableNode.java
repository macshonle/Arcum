package edu.ucsd.arcum.interpreter.fragments;

import org.eclipse.jdt.core.dom.*;

import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;

public class VariableNode extends ProgramFragment
{
    public static final String DONT_CARE = "<DONT_CARE>";
    private String id;
    private EntityType entityType;

    public VariableNode(EntityType entityType, String id) {
        this.entityType = entityType;
        if (id == null || id.equals(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE)) {
            this.id = DONT_CARE;
        }
        else {
            this.id = id;
        }
    }

    public final EntityType getNodeType() {
        return entityType;
    }

    @Override protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append("`");
        buff.append(id);
        buff.append(" (a ");
        buff.append(getNodeType().toString());
        buff.append(")");
    }

    @Override protected BindingMap matchesASTNode(ASTNode astNode) {
        return bindNamedRoot(astNode, id, entityType);
    }

    @Override protected BindingMap matchesEmpty(EmptyEntityInfo info) {
        // URGENT: make the right pseudo expression
        StructuralPropertyDescriptor edge = info.getEdge();
        ASTNode parentNode = info.getParent();
        if (edge == MethodInvocation.EXPRESSION_PROPERTY
            && parentNode instanceof MethodInvocation)
        {
            MethodInvocation methodInvocation = (MethodInvocation)parentNode;
            IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
            int modifiers = methodBinding.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                ITypeBinding declaringClass = methodBinding.getDeclaringClass();
                String qualifiedName = declaringClass.getQualifiedName();
                Type pseudoType = FragmentParser.getType(qualifiedName);
                return bindRoot(pseudoType);
            }
            else {
                ThisExpression pseudoTarget = parentNode.getAST().newThisExpression();
                return bindNamedRoot(pseudoTarget, id, EntityType.EXPR);
            }
        }
        return BindingMap.newEmptyMap();
    }

    @Override protected BindingMap matchesEntityList(EntityList list) {
        return bindNamedRoot(list, id, EntityType.PUNT);
    }

    @Override protected BindingMap matchesModifierElement(ModifierElement modifier) {
        return bindNamedRoot(modifier, id, EntityType.MODIFIERS);
    }

    @Override protected BindingMap matchesSimpleProperty(Object simple) {
        return bindNamedRoot(simple, id, EntityType.PUNT);
    }

    @Override protected BindingMap matchesTypeBinding(ITypeBinding typeBinding) {
        return bindNamedRoot(typeBinding, id, EntityType.TYPE);
    }

    @Override public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        Object entity = lookup.lookupEntity(id);
        if (entity instanceof ASTNode) {
            entity = Entity.copySubtree(ast, (ASTNode)entity);
        }
        return bindNamedRoot(entity, id, EntityType.PUNT);
    }

    public String getId() {
        return id;
    }
}