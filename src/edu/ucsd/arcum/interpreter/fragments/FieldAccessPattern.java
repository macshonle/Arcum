package edu.ucsd.arcum.interpreter.fragments;

import static edu.ucsd.arcum.interpreter.ast.JDTFacade.declaredClassOf;
import static edu.ucsd.arcum.interpreter.ast.JDTFacade.declaresVariableNamed;

import org.eclipse.jdt.core.dom.*;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.transformation.Conversion;

public class FieldAccessPattern extends ProgramFragment
{
    private final ProgramFragment targetPattern;
    private final ProgramFragment fieldPattern;

    private boolean isLeftHandSide = false;

    public FieldAccessPattern(ProgramFragment targetExpr,
        ProgramFragment field, FragmentParser fragmentParser)
    {
        this.targetPattern = targetExpr;
        this.fieldPattern = field;
    }

    @Override protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append(String.format("(FieldAccessPattern%n"));
        getIndenter().indent();
        {
            buff.append(getIndenter());
            buff.append(String.format("<targetPattern>%n"));
            targetPattern.buildString(buff);
            buff.append(String.format("%n"));
            buff.append(getIndenter());
            buff.append(String.format("<fieldPattern>%n"));
            fieldPattern.buildString(buff);
            buff.append(")");
        }
        getIndenter().unindent();
    }

    private boolean partOfLHS(ASTNode node) {
        if (isLeftHandSide) {
            // then it's ok if we're part of an assignment: act like we aren't
            return false;
        }
        ASTNode parent = node.getParent();
        if (parent instanceof Assignment) {
            Assignment assignment = (Assignment)parent;
            StructuralPropertyDescriptor spd = node.getLocationInParent();
            if (spd == Assignment.LEFT_HAND_SIDE_PROPERTY) {
                return true;
            }
        }
        return false;
    }

    // Is this Name actually an expression, or just a name that appears in a
    // "package" statement (for example)
    private boolean realSubexpression(Name name) {
        ASTNode namesParent = name.getParent();
        StructuralPropertyDescriptor spd = name.getLocationInParent();

        if (spd == MethodInvocation.NAME_PROPERTY) {
            // the name of a method is not a sub-expression
            return false;
        }

        if (namesParent instanceof Expression)
            return true;

        if (namesParent instanceof ExpressionStatement)
            return true;

        if (spd == VariableDeclarationFragment.INITIALIZER_PROPERTY)
            return true;

        return false;
    }

    // Assume fieldVariable is a field and name is the node used to reference it
    // (possibly qualified)
    private ASTNode extractFieldTarget(Name name, IVariableBinding fieldVariable) {
        IVariableBinding fieldDecl = fieldVariable.getVariableDeclaration();
        int flags = fieldDecl.getModifiers();
        if (Modifier.isStatic(flags)) {
            // Then a Type must be the target
            ITypeBinding declaringClass = fieldVariable.getDeclaringClass();
            Type type = FragmentParser.getType(declaringClass.getQualifiedName());
            return type;
        }
        else {
            if (name instanceof SimpleName) {
                // It's a field, but just a single name: must be an implicit 'this'
                return name.getAST().newThisExpression();
            }
            else {
                // Otherwise, some instance is the target
                return ((QualifiedName)name).getQualifier();
            }
        }

    }

    public void setLeftHandSide(boolean isLeftHandSide) {
        this.isLeftHandSide = isLeftHandSide;
    }

    // MACNEIL: We're being sloppy with the fieldPattern here: Should treat it more
    // like targetPattern and get it into the bindings as well.
    @Override public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        BindingMap targetMap = targetPattern.generateNode(lookup, ast);
        Object target = targetMap.getResult();
        SimpleName fieldName = getFieldName(lookup, ast);
        Expression targetExpr;
        if (target instanceof ITypeBinding) {
            target = Conversion.typeBindingtoASTNode(ast, (ITypeBinding)target);
        }
        if (target instanceof Type) {
            // Static field: target is a Type, not an Expression
            targetExpr = ast.newName(((Type)target).toString());
        }
        else {
            // (Probably) Non-static field: target is an Expression
            targetExpr = (Expression)target;
        }
        FieldAccess fieldAccess = ast.newFieldAccess();
        fieldAccess.setExpression(targetExpr);
        fieldAccess.setName(fieldName);
        return bindRoot(fieldAccess).consistentMerge(targetMap);
    }

    private SimpleName getFieldName(IEntityLookup lookup, AST ast) {
        Object fieldEntity;
        if (fieldPattern instanceof ResolvedEntity) {
            ResolvedEntity entity = (ResolvedEntity)fieldPattern;
            fieldEntity = entity.getValue();
        }
        else if (fieldPattern instanceof VariableNode) {
            VariableNode variableNode = (VariableNode)fieldPattern;
            String id = variableNode.getId();
            fieldEntity = lookup.lookupEntity(id);
        }
        else {
            ArcumError.fatalError("Expected fieldPattern to be resolved or lookupable");
            return null;
        }
        String id;
        if (fieldEntity instanceof SimpleName) {
            id = ((SimpleName)fieldEntity).getIdentifier();
        }
        else if (fieldEntity instanceof FieldDeclaration) {
            FieldDeclaration fieldDecl = (FieldDeclaration)fieldEntity;
            VariableDeclarationFragment varDecl = (VariableDeclarationFragment)fieldDecl
                .fragments().get(0);
            id = varDecl.getName().getIdentifier();
        }
        else if (fieldEntity instanceof String) {
            id = ((String)fieldEntity);
        }
        else {
            ArcumError.fatalError("Can't cope!%n");
            return null;
        }
        return ast.newSimpleName(id);
    }

    @Override protected BindingMap matchesASTNode(ASTNode node) {
        if (node == null) {
            System.err.printf("gulp, this is bad%n");
            return null;
        }

        ASTNode target; // the target expr, or class (in the case of static)
        ITypeBinding declaringClass; // the class that has the field
        String fieldName; // the field's name

        if (partOfLHS(node)) {
            return null;
        }

        if (node instanceof FieldAccess) {
            FieldAccess access = (FieldAccess)node;
            SimpleName id = access.getName();
            IVariableBinding binding = (IVariableBinding)EntityDataBase.resolveBinding(id);

            target = access.getExpression();
            declaringClass = binding.getDeclaringClass();
            fieldName = id.getIdentifier();
        }
        else if (node instanceof Name) {
            Name name = (Name)node;
            Name topName = name;
            for (;;) {
                ASTNode parent = topName.getParent();
                if (!(parent instanceof Name))
                    break;
                topName = (Name)parent;
            }

            if (name instanceof SimpleName) {
                if (partOfLHS(topName)) {
                    return null;
                }
                ASTNode parentExpr = topName.getParent();
                if (parentExpr instanceof FieldAccess) {
                    if (partOfLHS(parentExpr)) {
                        return null;
                    }
                }
            }

            if (!realSubexpression(topName)) {
                return null;
            }

            IBinding binding = EntityDataBase.resolveBinding(name);
            if (binding.getKind() == IBinding.VARIABLE) {
                IVariableBinding var = (IVariableBinding)binding;
                if (var.isField()) {
                    target = extractFieldTarget(name, var);
                    declaringClass = var.getDeclaringClass();
                    fieldName = var.getName();
                }
                else
                    return null;
            }
            else
                return null;
        }
        else
            return null;

        if (fieldPattern instanceof ResolvedEntity) {
            ResolvedEntity resEntity = (ResolvedEntity)fieldPattern;
            Object fieldEntity = resEntity.getValue();
            if (fieldEntity instanceof ASTNode) {
                if (fieldEntity instanceof FieldDeclaration) {
                    FieldDeclaration field = (FieldDeclaration)fieldEntity;
                    if (!declaresVariableNamed(field, fieldName)
                        || !declaredClassOf(field).isEqualTo(declaringClass))
                    {
                        return null;
                    }
                }
                else if (fieldEntity instanceof SimpleName) {
                    SimpleName simpleName = (SimpleName)fieldEntity;
                    if (!simpleName.getIdentifier().equals(fieldName)) {
                        return null;
                    }
                }
                else {
                    ArcumError.fatalError("Can't handle ast-node case where field is"
                        + " a %s%n", fieldEntity.getClass().toString());

                }
            }
            else if (fieldEntity instanceof String) {
                String string = (String)fieldEntity;
                if (!string.equals(fieldName)) {
                    return null;
                }
            }
            else {
                ArcumError.fatalError("Can't handle case where field is a %s%n",
                    fieldEntity.getClass().toString());
            }
        }
        else
            return null;

        BindingMap targetMatches = targetPattern.matches(target);
        if (targetMatches != null) {
            BindingMap result = bindRoot(node);
            result.addBindings(targetMatches);
            return result;
        }
        else {
            return null;
        }
    }
}