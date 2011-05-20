package edu.ucsd.arcum.interpreter.query;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.dom.*;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.ASTUtil;
import edu.ucsd.arcum.interpreter.ast.ASTUtil.ASTPath;
import edu.ucsd.arcum.interpreter.fragments.ModifierElement;
import edu.ucsd.arcum.interpreter.fragments.SignatureEntity;
import edu.ucsd.arcum.interpreter.fragments.Union;
import edu.ucsd.arcum.interpreter.transformation.CodeRewriter;
import edu.ucsd.arcum.util.StringUtil;
import edu.ucsd.arcum.util.SystemUtil;

public abstract class Entity
{
    public static boolean isModifiersEdge(StructuralPropertyDescriptor edge) {
        return edge == TypeDeclaration.MODIFIERS2_PROPERTY
            || edge == EnumDeclaration.MODIFIERS2_PROPERTY
            || edge == AnnotationTypeDeclaration.MODIFIERS2_PROPERTY
            || edge == MethodDeclaration.MODIFIERS2_PROPERTY;
    }

    public static String getDisplayString(@Union("Entity") Object entity) {
        if (entity == null) {
            return "-";
        }
        else if (entity instanceof ITypeBinding) {
            return ((ITypeBinding)entity).getQualifiedName();
        }
        else {
            String result;
            if (entity instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration decl = (AbstractTypeDeclaration)entity;
                SimpleName name = decl.getName();
                result = String.format("%s", name.toString());
            }
            else {
                result = entity.toString();
            }
            return StringUtil.minimizeWhitespace(result);
        }
    }

    public static String getQualifiedName(Object entity) {
        if (entity instanceof ITypeBinding) {
            return ((ITypeBinding)entity).getQualifiedName();
        }
        return entity.toString().trim();
    }

    // Two AST nodes might be equal in tree structure, but for this method location
    // matters. If the nodes have no parent, then they don't have a location yet,
    // and are considered equal. If they do have a parent, then we use identity
    // semantics
    public static int compareToWithLocations(Object thiz, Object that) {
        int result;
        if (thiz instanceof ASTNode && that instanceof ASTNode) {
            if (((ASTNode)thiz).getParent() == null
            // URGENT (!!!): There's a question if this should be && or ||. My
                // feeling is that one of them being null is sufficient, not both.
                || ((ASTNode)that).getParent() == null)
            {
                result = Entity.compareTo(thiz, that);
            }
            else if (thiz == that) {
                result = 0;
            }
            else {
                result = SystemUtil.compareIdentityCodesConsistently(thiz, that);
            }
        }
        else {
            // At least one entity is not an ASTNode; e.g. one/both might be a
            // type binding instead
            result = Entity.compareTo(thiz, that);
        }
        return result;
    }

    // MONDAY: Consider ITypeBinding and IMethodBindings to be canonicalized and
    // represented as the keys (as Strings)
    public static int compareTo(Object thiz, Object that) {
        if (thiz == that || thiz.equals(that)) {
            return 0;
        }
        else {
            boolean result;
            if (thiz instanceof Type && that instanceof Type) {
                result = compareTypes((Type)thiz, (Type)that);
            }
            else if (thiz instanceof Type) {
                result = compareTypeToX((Type)thiz, that);
            }
            else if (that instanceof Type) {
                result = compareTypeToX((Type)that, thiz);
            }
            else if (thiz instanceof BindingKeyValue && that instanceof BindingKeyValue) {
                result = thiz.equals(that);
            }
            else if (thiz instanceof BindingKeyValue && that instanceof ASTNode) {
                result = compareBindingToASTNode((BindingKeyValue)thiz, (ASTNode)that);
            }
            else if (thiz instanceof ASTNode && that instanceof BindingKeyValue) {
                result = compareBindingToASTNode((BindingKeyValue)that, (ASTNode)thiz);
            }
            else if (thiz instanceof ITypeBinding && that instanceof ITypeBinding) {
                result = compareTypeBindings((ITypeBinding)thiz, (ITypeBinding)that);
            }
            else if (thiz instanceof TypeDeclaration && that instanceof ITypeBinding) {
                result = compareTypeDeclarationToTypeBinding((TypeDeclaration)thiz,
                    (ITypeBinding)that);
            }
            else if (thiz instanceof ITypeBinding && that instanceof TypeDeclaration) {
                result = compareTypeDeclarationToTypeBinding((TypeDeclaration)that,
                    (ITypeBinding)thiz);
            }
            else if (thiz instanceof SimpleName && that instanceof String) {
                result = compareSimpleNameAndString(thiz, that);
            }
            else if (thiz instanceof String && that instanceof SimpleName) {
                result = compareSimpleNameAndString(that, thiz);
            }
            else if (thiz instanceof Name && that instanceof ITypeBinding) {
                result = compareNameAndTypeBinding((Name)thiz, (ITypeBinding)that);
            }
            else if (thiz instanceof ITypeBinding && that instanceof Name) {
                result = compareNameAndTypeBinding((Name)that, (ITypeBinding)thiz);
            }
            else if (thiz instanceof Name && that instanceof Type) {
                result = compareNameAndType(thiz, that);
            }
            else if (thiz instanceof Name && that instanceof Type) {
                result = compareNameAndType(thiz, that);
            }
            else if (thiz instanceof ModifierElement && that instanceof Modifier) {
                result = compareModifierElementAndModifier((ModifierElement)thiz,
                    (Modifier)that);
            }
            else if (thiz instanceof Modifier && that instanceof ModifierElement) {
                result = compareModifierElementAndModifier((ModifierElement)that,
                    (Modifier)thiz);
            }
            else if (thiz instanceof SignatureEntity && that instanceof MethodDeclaration)
            {
                result = compareSignatureEntityAndASTNode((SignatureEntity)thiz,
                    (MethodDeclaration)that);
            }
            else if (thiz instanceof MethodDeclaration && that instanceof SignatureEntity)
            {
                result = compareSignatureEntityAndASTNode((SignatureEntity)that,
                    (MethodDeclaration)thiz);
            }
            else if (thiz instanceof ASTNode) {
                if (thiz instanceof Name && that instanceof Name) {
                    Name n1 = (Name)thiz;
                    ITypeBinding tb1 = n1.resolveTypeBinding();
                    if (tb1 != null) {
                        String qualifiedName = tb1.getQualifiedName();
                        if (qualifiedName.equals(((Name)that).getFullyQualifiedName()))
                            return 0;
                    }
                }
                result = ((ASTNode)thiz).subtreeMatch(
                    new ModuloQualificationASTMatcher(), that);
            }
            else {
                result = false;
            }
            // TASK !!!!!
//            if (result == false) {
//                System.out.printf("%s != %s%n", getDisplayString(thiz), getDisplayString(that));
//                System.out.printf("%s != %s%n", StringUtil.debugDisplay(thiz), StringUtil.debugDisplay(that));
//            }
            return booleanToTroolian(result, thiz, that);
        }
    }

    private static boolean compareBindingToASTNode(BindingKeyValue thiz, ASTNode that) {
        IBinding binding = thiz.getOriginalBinding();
        ASTNode foundAST = EntityDataBase.findASTNode(binding);
        return compareToWithLocations(foundAST, that) == 0;
    }

    private static boolean compareTypeToX(Type type, Object something) {
        boolean result;
        ITypeBinding nodeBinding = (ITypeBinding)EntityDataBase
            .resolveBindingNullOK(type);
        if (nodeBinding != null) {
            if (something instanceof ITypeBinding) {
                ITypeBinding valueBinding = (ITypeBinding)something;
                result = compareTypeBindings(nodeBinding, valueBinding);
            }
            else {
                result = nodeBinding.equals(something);
            }
        }
        else {
            result = compareTypeToValue(type, something);
        }
        return result;
    }

    private static boolean compareTypeDeclarationToTypeBinding(
        TypeDeclaration typeDeclaration, ITypeBinding tb2)
    {
        ITypeBinding tb1 = (ITypeBinding)EntityDataBase.resolveBinding(typeDeclaration);
        return compareTypeBindings(tb1, tb2);
    }

    private static boolean compareTypes(Type t1, Type t2) {
        boolean result;
        ITypeBinding tb1 = (ITypeBinding)EntityDataBase.resolveBindingNullOK(t1);
        ITypeBinding tb2 = (ITypeBinding)EntityDataBase.resolveBindingNullOK(t2);
        if (tb1 != null && tb2 != null) {
            result = tb1.isEqualTo(tb2);
        }
        else {
            // in some cases, we are comparing against our own generated
            // code, so it's possible for these to match
            result = t1.subtreeMatch(new ModuloQualificationASTMatcher(), t2);
        }
        return result;
    }

    private static boolean compareNameAndType(Object thiz, Object that) {
        boolean result;
        Name thizName = (Name)thiz;
        IBinding tb1 = EntityDataBase.resolveBinding(thizName);
        Type thatType = (Type)that;
        ITypeBinding tb2 = (ITypeBinding)EntityDataBase.resolveBinding(thatType);
        result = tb1.isEqualTo(tb2);
        return result;
    }

    private static boolean compareNameAndTypeBinding(Name name, ITypeBinding tb1) {
        ITypeBinding tb2 = name.resolveTypeBinding();
        if (tb2 != null) {
            if (tb1.isEqualTo(tb2)) {
                if (DEBUG) {
                    System.out.printf("%s is indeed equal to %s%n", name, tb1);
                }
                return true;
            }
            else {
                return false;
            }
        }
        else {
            System.err
                .printf("Type information is unavailable: possible internal error%n");
            String nameRep = name.toString();
            String tb1Rep = tb1.getQualifiedName();
            boolean result = nameRep.equals(tb1Rep);
            return result;
        }
    }

    private static boolean compareSimpleNameAndString(Object thiz, Object that) {
        boolean result;
        SimpleName thisName = (SimpleName)thiz;
        result = thisName.getIdentifier().equals(that);
        return result;
    }

    private static boolean compareTypeBindings(ITypeBinding tb1, ITypeBinding tb2) {
        if (tb1.isEqualTo(tb2)) {
            return true;
        }
        else {
            String node = tb1.getQualifiedName();
            String value = tb2.getQualifiedName();

            if (DEBUG && node.equals(value)) {
                System.out.printf("Found: %s equals %s.%n", node, value);
            }

            return node.equals(value);
        }
    }

    private static boolean compareModifierElementAndModifier(
        ModifierElement modifierElement, Modifier modifier)
    {
        return modifierElement.isSameModifier(modifier);
    }

    private static boolean compareSignatureEntityAndASTNode(SignatureEntity signature,
        MethodDeclaration methodDecl)
    {
        IMethodBinding binding = methodDecl.resolveBinding();
        boolean result = signature.hasSameSignatureAs(binding);
        return result;
    }

    private static boolean compareTypeToValue(Type type, Object value) {
        String name = type.toString();
        if (value instanceof ITypeBinding) {
            ITypeBinding valueBinding = (ITypeBinding)value;
            String qualifiedName = valueBinding.getQualifiedName();
            if (name.equals(qualifiedName)) {
                return true;
            }
            else {
                String lastSegment = qualifiedName.substring(qualifiedName
                    .lastIndexOf(".") + 1);
                if (name.equals(lastSegment)) {
                    // there's a chance they do match... FIXME: don't be so optimistic,
                    // however, this nitty-gritty case might only occur when we are
                    // comparing against AST nodes we've created ourselves (because the
                    // better type information would otherwise be available) and thus
                    // matching won't be too affected. We could try to find the imports
                    // and compare it against the qualifiedName.
                    return true;
                }
                return false;
            }
        }
        else {
            return value.equals(type);
        }
    }

    // If value is true, then 0 is returned. Otherwise, some consistent ordering
    // between a and b is returned based on their identity hash codes.
    private static int booleanToTroolian(boolean value, Object a, Object b) {
        if (value) {
            return 0;
        }
        else {
            String aStr = getDisplayString(a);
            String bStr = getDisplayString(b);
            return aStr.compareTo(bStr);
            // The problem with using the identity codes is that type bindings
            // aren't consistent, because they are not the same object always. Two
            // "equivalent" type bindings, however, will have the same display
            // string, so those are compared for a consistent ordering instead
//            return SystemUtil.compareIdentityCodesConsistently(a, b);
        }
    }

    public static ASTNode getASTNodeValue(Object entity) {
        if (entity instanceof ASTNode) {
            ASTNode result = (ASTNode)entity;
            result = ASTUtil.queryUpdatedNode(result);
            return (ASTNode)result;
        }
        else {
            return null;
        }
    }

    public static String valueAsString(Object entity) {
        if (entity instanceof ITypeBinding) {
            return ((ITypeBinding)entity).getQualifiedName();
        }
        else if (entity == null) {
            return "null";
        }
        return entity.toString().trim();
    }

    public static <T extends ASTNode> T copySubtree(AST target, T node) {
        T copy = (T)ASTNode.copySubtree(target, node);
        ASTUtil.recordUpdatedNode(node, copy);
        List<ASTNode> trackedNodes = CodeRewriter.findAllTrackedNodes(node);
        for (ASTNode trackedNode : trackedNodes) {
            Object id = CodeRewriter.getTrackingID(trackedNode);
            ASTPath pathToRoot = ASTUtil.getPathToRoot(trackedNode, node);
            ASTNode toTrack = pathToRoot.getASTNodeFrom(copy);
            CodeRewriter.setTrackingID(toTrack, id);
        }
        return copy;
    }

    public static ITypeBinding getTypeOf(Object entity) {
        if (entity instanceof Type) {
            Type type = (Type)entity;
            ITypeBinding binding = (ITypeBinding)EntityDataBase.resolveBinding(type);
            return binding;
        }
        else if (entity instanceof ITypeBinding) {
            return (ITypeBinding)entity;
        }
        else if (entity instanceof BindingKeyValue) {
            BindingKeyValue keyValue = (BindingKeyValue)entity;
            EntityType type = keyValue.getType();
            if (type == EntityType.TYPE) {
                ITypeBinding binding = (ITypeBinding)keyValue.getOriginalBinding();
                return binding;
            }
            else if (type == EntityType.METHOD) {
                IMethodBinding binding = (IMethodBinding)keyValue.getOriginalBinding();
                // NOTE: The typeOf a constructor could be the type of the class,
                // instead of void as returned by this method:
                return binding.getReturnType();
            }
            else {
                ArcumError.fatalError("Cannot handle %s case in getTypeOf", type);
                return null;
            }
        }
        else {
            ASTNode node = Entity.getASTNodeValue(entity);
            ITypeBinding typeBinding = null;
            if (node instanceof Expression) {
                Expression expression = (Expression)node;
                typeBinding = expression.resolveTypeBinding();
            }
            else if (node instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)node;
                List fragments = fieldDecl.fragments();
                VariableDeclarationFragment varDecl;
                varDecl = (VariableDeclarationFragment)fragments.get(0);
                SimpleName name = varDecl.getName();
                typeBinding = typeOfName(name);
            }
            else if (node instanceof MethodDeclaration) {
                ArcumError.fatalError("Not implemented yet in getTypeOf");
                return null;
            }
            return typeBinding;
        }
    }

    private static ITypeBinding typeOfName(Name name) {
        ITypeBinding typeBinding;
        IBinding binding = EntityDataBase.resolveBinding(name);
        switch (binding.getKind()) {
        case IBinding.VARIABLE:
            IVariableBinding var = (IVariableBinding)binding;
            typeBinding = var.getType();
            break;
        case IBinding.TYPE:
            typeBinding = (ITypeBinding)binding;
            break;
        default:
            ArcumError.fatalError("Unhandled typeOfName case: %s", binding);
            return null/*unreachable*/;
        }
        return typeBinding;
    }

    public static Object canonicalizeRepresentation(Object entity) {
        if (entity instanceof MethodDeclaration) {
            MethodDeclaration methodDeclaration = (MethodDeclaration)entity;
            IMethodBinding binding = methodDeclaration.resolveBinding();
            if (binding != null) {
                return BindingKeyValue.newInstance(EntityType.METHOD, binding);
            }
            // otherwise it is synthetic
        }
        return entity;
    }

    // Returns true when all "elements" of the given set are found in the "nodes" set 
    public static <T> boolean subsetOf(Iterable<T> elements, Collection<ASTNode> nodes)
    {
        nextElement: for (T element : elements) {
            for (ASTNode node : nodes) {
                if (compareTo(element, node) == 0) {
                    continue nextElement;
                }
            }
            return false;
        }
        return true;
    }

    public static boolean isReturnType(Type type) {
        StructuralPropertyDescriptor spd = type.getLocationInParent();
        return spd == MethodDeclaration.RETURN_TYPE2_PROPERTY;
    }
}