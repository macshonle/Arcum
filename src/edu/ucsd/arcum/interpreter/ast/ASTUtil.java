package edu.ucsd.arcum.interpreter.ast;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.*;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.Unreachable;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.transformation.Conversion;
import edu.ucsd.arcum.util.StringUtil;

public class ASTUtil
{
    // EXAMPLE: Eliminate this class and use the simple Google Function<String,T> class
    // in the interfaces instead
    public interface NameAccessor<T>
    {
        String getName(T element);
    }

    public static final NameAccessor<String> IDENTITY_ACCESSOR = new NameAccessor<String>() {
        public String getName(String element) {
            return element;
        }
    };

    public static final NameAccessor<FormalParameter> PARAMETER_NAME = new NameAccessor<FormalParameter>() {
        public String getName(FormalParameter param) {
            return param.getIdentifier();
        }
    };

    public static <T> List<T> flatten(Collection<List<T>> collection) {
        List<T> result = new ArrayList<T>();
        for (List<T> list : collection) {
            result.addAll(list);
        }
        return result;
    }

    public static <T> void checkNames(List<T> list, NameAccessor<T> accessor) {
        checkNames(list, accessor, "The name %s cannot be used"
            + " multiple times in the same context");
    }

    // EXAMPLE: Soon -- Handling cases like this where really every syntactic element
    // should have a location, so that calls to fatalError would become fatalUserError
    // Many alternative implementations are conceivable: 1) No locations, so dialogs;
    // 2) locations stored externally; 3) locations stored internally
    public static <T> void checkNames(List<T> list, NameAccessor<T> accessor,
        String formattedMessage)
    {
        Set<String> names = new HashSet<String>();
        for (T element : list) {
            String name = accessor.getName(element);
            if (names.contains(name)) {
                ArcumError.fatalError(String.format(formattedMessage, name));
            }
            names.add(name);
        }
    }

    public static <T> void extractNames(Collection<String> dest, Collection<T> list,
        NameAccessor<T> accessor)
    {
        for (T element : list) {
            dest.add(accessor.getName(element));
        }
    }

    public static <T> T find(String name, Collection<T> list, NameAccessor<T> accessor) {
        for (T element : list) {
            if (accessor.getName(element).equals(name)) {
                return element;
            }
        }
        return null;
    }

    // Given a set of nodes, returns a subset of the nodes that do not have as
    // parents (grand-parents, etc...) any of the other nodes.
    public static List<ASTNode> findTrees(Collection<ASTNode> nodes) {
        List<ASTNode> candidates = Lists.newArrayList(nodes);
        Iterator<ASTNode> it = candidates.iterator();
        nextElement: while (it.hasNext()) {
            ASTNode curElement = it.next();
            ASTNode expecting = curElement;
            while (expecting != null) {
                expecting = expecting.getParent();
                if (nodes.contains(expecting)) {
                    // it can't be us, because another node is our parent
                    it.remove();
                    continue nextElement;
                }
            }
        }
        return candidates;
    }
    
    public static Type buildTypeNode(AST ast, ITypeBinding binding) {
        if (binding.isPrimitive()) {
            Code code = PrimitiveType.toCode(binding.getName());
            return ast.newPrimitiveType(code);
        }
        else if (binding.isArray()) {
            ITypeBinding elementType = binding.getElementType();
            int dimensions = binding.getDimensions();
            return ast.newArrayType(buildTypeNode(ast, elementType), dimensions);
        }
        else if (binding.isGenericType()) {
            String qualifiedName = binding.getQualifiedName();
            Name name = ast.newName(qualifiedName);
            return ast.newSimpleType(name);
        }
        else if (binding.isParameterizedType() || binding.isRawType()) {
            ITypeBinding[] typeArguments = binding.getTypeArguments();
            ITypeBinding typeDeclaration = binding.getTypeDeclaration();
            ParameterizedType type;
            type = ast.newParameterizedType(buildTypeNode(ast, typeDeclaration));
            for (ITypeBinding typeArgument : typeArguments) {
                ASTNode typeArgumentNode = buildTypeNode(ast, typeArgument);
                typeArgumentNode = Conversion.cleanseASTNode(ast, typeArgumentNode);
                type.typeArguments().add(typeArgumentNode);
            }
            return type;
        }
        else {
            return FragmentParser.getType(binding.getQualifiedName());
        }
    }

    private static final Map<ASTNode, ASTNode> sugarTable;
    private static final Map<ASTNode, ITypeBinding> parentTable;

    static {
        sugarTable = Maps.newIdentityHashMap();
        parentTable = Maps.newIdentityHashMap();
    }

    public static <T extends ASTNode> void recordUpdatedNode(T original, T replacement) {
        ASTNode root = replacement.getRoot();
        sugarTable.put(root, original);
    }

    public static ASTNode queryUpdatedNode(ASTNode node) {
        ASTNode result = sugarTable.get(node);
        if (result != null) {
            return result;
        }
        else {
            return node;
        }
    }

    public static void recordNewParent(ASTNode member, ITypeBinding parent) {
        if (DEBUG) {
            System.out.printf("Recording %s is a member of %s%n", member, parent);
        }
        parentTable.put(member, parent);
    }

    public static ITypeBinding queryNewParentOf(ASTNode member) {
        ITypeBinding result = parentTable.get(member);
        return result;
    }

    // Returns null when "node" has been generated and was not found in the original
    // AST
    public static CompilationUnit findCompilationUnit(ASTNode node) {
        if (node == null) {
            return null;
        }
        ASTNode root = node.getRoot();
        if (root instanceof CompilationUnit) {
            return (CompilationUnit)root;
        }
        else {
            if (DEBUG) {
                System.out.printf("For %s the sugarTable is used!%n", root);
            }
            root = root.getRoot();
            return findCompilationUnit(sugarTable.get(root));
        }
    }

    public static Object getDebugString(Object entity) {
        if (entity instanceof ASTNode) {
            ASTNode node = (ASTNode)entity;
            ASTNode root = node.getRoot();
            return String.format("%s ([a %s], startPos=%d, root=%s)", Entity
                .getDisplayString(node), node.getClass().getSimpleName(), node
                .getStartPosition(), root.getClass().getSimpleName());
        }
        else if (entity == null) {
            return "{{null}}";
        }
        else {
            return String.format("{{%s}}", StringUtil.debugDisplay(entity));
        }
    }

    // Returns the method that this expression is defined in, or null if the
    // expression is not contained in a method.
    // MACNEIL: Note that inner-classes can have field initializations that might or
    // might not be considered actually called by the method returned.
    public static IMethodBinding getDefiningMethod(Expression expr) {
        ASTNode parent = expr.getParent();
        while (!(parent instanceof MethodDeclaration)
            && !(parent instanceof CompilationUnit))
        {
            // MACNEIL: A null pointer exception here means the sugar table should
            // be used instead
            parent = parent.getParent();
        }
        if (parent instanceof CompilationUnit) {
            return null;
        }
        else {
            MethodDeclaration decl = (MethodDeclaration)parent;
            return decl.resolveBinding();
        }
    }

    public static List<Annotation> getAnnotations(ASTNode node) {
        List modifiersAndAnnotations = getExtendedModifiers(node);
        List<Annotation> result = Lists.newArrayList();
        for (Object modOrAnnot : modifiersAndAnnotations) {
            if (modOrAnnot instanceof Annotation) {
                result.add((Annotation)modOrAnnot);
            }
        }
        return result;
    }

    public static List<IExtendedModifier> getExtendedModifiers(ASTNode node) {
        List result;
        if (node instanceof Type) {
            Type type = (Type)node;
            if (Entity.isReturnType(type)) {
                ASTNode method = type.getParent();
                List<IExtendedModifier> extendedModifiers = getExtendedModifiers(method);
                result = Lists.newArrayList();
                // All of the modifiers apply to the method, not the return type, so
                // we only pick out annotations whose targets are types
                // VERSION2: We need to find out how to get that target
                for (IExtendedModifier modifier : extendedModifiers) {
                    if (modifier.isAnnotation()) {
                        Annotation annotation = (Annotation)modifier;
                        result.add(annotation);
                    }
                }
                return result;
            }
        }
        if (node instanceof SingleVariableDeclaration) {
            SingleVariableDeclaration svd = (SingleVariableDeclaration)node;
            result = svd.modifiers();
        }
        else if (node instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment vdf = (VariableDeclarationFragment)node;
            ASTNode parent = vdf.getParent();
            if (parent instanceof VariableDeclarationExpression) {
                VariableDeclarationExpression vde = (VariableDeclarationExpression)parent;
                result = vde.modifiers();
            }
            else {
                ArcumError.fatalError("Handle case where parent is: %s", //->
                    ASTUtil.getDebugString(parent));
                return null;
            }
        }
        else if (node instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement vds = (VariableDeclarationStatement)node;
            result = vds.modifiers();
        }
        else if (node instanceof FieldDeclaration) {
            FieldDeclaration fieldDecl = (FieldDeclaration)node;
            result = fieldDecl.modifiers();
        }
        else if (node instanceof MethodDeclaration) {
            MethodDeclaration methodDecl = (MethodDeclaration)node;
            result = methodDecl.modifiers();
        }
        else {
            ArcumError.fatalError("Handle case where node is: %s", //->
                ASTUtil.getDebugString(node));
            throw new Unreachable();
        }
        return result;
    }

    // Returns true when the given name (assumed to be found in a proper,
    // fully-parented AST) is a label or Javadoc comment element (as opposed to a
    // variable).
    public static boolean isLabel(Name name) {
        StructuralPropertyDescriptor spd = name.getLocationInParent();
        if (spd == LabeledStatement.LABEL_PROPERTY //->
            || spd == ContinueStatement.LABEL_PROPERTY //->
            || spd == BreakStatement.LABEL_PROPERTY //->
            || isJavadocCommentPart(spd, name))
        {
            return true;
        }
        else {
            return false;
        }
    }

    private static boolean isJavadocCommentPart(StructuralPropertyDescriptor spd, Name name) {
        if (spd == MethodRef.NAME_PROPERTY //->
            || spd == MethodRef.QUALIFIER_PROPERTY) {
            return true;
        }
        ASTNode expecting = name.getParent();
        while (expecting != null) {
            if (expecting instanceof Javadoc) {
                return true;
            }
            expecting = expecting.getParent();
        }
        return false;
    }

    public static ASTPath getPathToRoot(ASTNode node, ASTNode root) {
        ASTPath result = new ASTPath();
        while (node != root) {
            result.addEdge(node);
            node = node.getParent();
        }
        return result;
    }
    
    public static class ASTPath {
        final List<PathEdge> path;

        private ASTPath() {
            this.path = Lists.newArrayList();
        }
        
        private void addEdge(ASTNode node) {
            StructuralPropertyDescriptor spd = node.getLocationInParent();
            int index = 0;
            if (spd instanceof ChildListPropertyDescriptor) {
                ASTNode parent = node.getParent();
                List<?> list = (List<?>)parent.getStructuralProperty(spd);
                index = list.indexOf(node);
            }
            PathEdge edge = new PathEdge(spd, index);
            path.add(edge);
        }

        public ASTNode getASTNodeFrom(ASTNode node) {
            for (int i=path.size() - 1; i >=0; --i) {
                PathEdge edge = path.get(i);
                Object child = node.getStructuralProperty(edge.spd);
                if (child instanceof List) {
                    List<?> list = (List<?>)child;
                    node = (ASTNode)list.get(edge.index);
                }
                else {
                    node = (ASTNode)child;
                }
            }
            return node;
        }
        
        private static class PathEdge {
            private StructuralPropertyDescriptor spd;
            private int index;
            
            private PathEdge(StructuralPropertyDescriptor spd, int index) {
                this.spd = spd;
                this.index = index;
            }
        }
    }

    // Removes this ASTNode from it's parent
    public static void removeNodeFromParent(ASTNode node) {
        StructuralPropertyDescriptor spd = node.getLocationInParent();
        ASTNode parent = node.getParent();
        if (spd instanceof ChildPropertyDescriptor) {
            parent.setStructuralProperty(spd, null);
        }
        else if (spd instanceof ChildListPropertyDescriptor) {
            List<?> list = (List<?>)parent.getStructuralProperty(spd);
            int index = list.indexOf(node);
            list.remove(index);
        }
        else {
            parent.setStructuralProperty(spd, null);
        }
    }
}