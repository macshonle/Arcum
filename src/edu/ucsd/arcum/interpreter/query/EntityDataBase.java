package edu.ucsd.arcum.interpreter.query;

import static com.google.common.base.ReferenceType.WEAK;
import static edu.ucsd.arcum.ArcumPlugin.DEBUG;
import static edu.ucsd.arcum.interpreter.ast.FormalParameter.getIdentifier;
import static edu.ucsd.arcum.interpreter.query.EntityTuple.values;
import static edu.ucsd.arcum.util.Pair.newPair;

import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.dom.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ReferenceMap;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.ASTUtil;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.ast.expressions.BuiltInFunction;
import edu.ucsd.arcum.interpreter.ast.expressions.PatternExpression;
import edu.ucsd.arcum.interpreter.fragments.*;
import edu.ucsd.arcum.interpreter.parser.ASTVisitorAdaptor;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.satisfier.BindingsSet;
import edu.ucsd.arcum.interpreter.satisfier.MaskedLookup;
import edu.ucsd.arcum.interpreter.satisfier.TypeLookupTable;
import edu.ucsd.arcum.util.*;

public class EntityDataBase
{
    public static final Map<String, TraitSignature> BUILT_IN_TRAIT_TYPES;
    public static final String PARENT_VAR_REF = "parent";
    public static final String CHILD_VAR_REF = "child";
    public static final String SUPER_CLASS_VAR_REF = "superclass";
    public static final String SUB_CLASS_VAR_REF = "subclass";
    public static final String EXPR_VAR_REF = "expression";
    public static final String EXPR2_VAR_REF = "expression2";
    public static final String METHOD_VAR_REF = "method";
    public static final String DECLARATION_VAR_REF = "declaration";

    private static final Set<String> BUILT_IN_TRAIT_NAMES;
    private static final Set<String> ALL_BUILT_IN_NAMES;

    private static final DynamicScope<EntityDataBase> currentEDB = DynamicScope
        .newInstance();

    private static final Map<Class<?>, EntityType> entityTypeTable;

    static {
        entityTypeTable = Maps.newHashMap();

        List<TraitSignature> entries = Lists.newArrayList();
        // hasField({Class, Type}, Field)
        entries.add(TraitSignature.makeBuiltIn("hasField", new FormalParameter(
            EntityType.TYPE, PARENT_VAR_REF), new FormalParameter(EntityType.FIELD,
            CHILD_VAR_REF)));
        // hasMethod({Class, Type}, Method)
        entries.add(TraitSignature.makeBuiltIn("hasMethod", new FormalParameter(
            EntityType.TYPE, PARENT_VAR_REF), new FormalParameter(EntityType.METHOD,
            CHILD_VAR_REF)));
        // hasAnnotation({Type+,Method,Field,DeclarationElement}, Annotation)
        entries.add(TraitSignature.makeBuiltIn("hasAnnotation", new FormalParameter(
            EntityType.ANY, PARENT_VAR_REF), new FormalParameter(EntityType.ANNOTATION,
            CHILD_VAR_REF)));
        // invokes({Expr, Method}, Method)
        entries.add(TraitSignature.makeBuiltIn("invokes", new FormalParameter(
            EntityType.EXPR, EXPR_VAR_REF), new FormalParameter(EntityType.METHOD,
            METHOD_VAR_REF)));
        // hasInvocationTarget(Expr, Expr)
        entries.add(TraitSignature.makeBuiltIn("hasInvocationTarget", new FormalParameter(
            EntityType.EXPR, EXPR_VAR_REF), new FormalParameter(EntityType.EXPR,
                EXPR2_VAR_REF)));
        // declaredBy(Expr, DeclarationElement)
        entries.add(TraitSignature.makeBuiltIn("declaredBy", new FormalParameter(
            EntityType.EXPR, EXPR_VAR_REF), new FormalParameter(
            EntityType.DECLARATION_ELEMENT, DECLARATION_VAR_REF)));
        // copiedTo(Expr, DeclarationElement)
        entries.add(TraitSignature.makeBuiltIn("copiedTo", new FormalParameter(
            EntityType.EXPR, EXPR_VAR_REF), new FormalParameter(
            EntityType.DECLARATION_ELEMENT, DECLARATION_VAR_REF)));
        // superclassOf(Class, Class)
        entries.add(TraitSignature.makeBuiltIn("superclassOf", new FormalParameter(
            EntityType.TYPE, SUPER_CLASS_VAR_REF), new FormalParameter(EntityType.TYPE,
            SUB_CLASS_VAR_REF)));

        BUILT_IN_TRAIT_TYPES = Maps.newHashMap();
        for (TraitSignature entry : entries) {
            BUILT_IN_TRAIT_TYPES.put(entry.getName(), entry);
        }

        BUILT_IN_TRAIT_NAMES = Sets.immutableSet(BUILT_IN_TRAIT_TYPES.keySet());
        Set<String> allNames = new HashSet<String>();
        allNames.addAll(BUILT_IN_TRAIT_NAMES);
        allNames.addAll(BuiltInFunction.getBuiltInNames());
        ALL_BUILT_IN_NAMES = Sets.immutableSet(allNames);
    }

    public static Set<String> getBuiltInTraitAndPredicateNames() {
        return ALL_BUILT_IN_NAMES;
    }

    private static final String PROGRESS_MESSAGE = "Done once. Later analyses are performed incrementally.";

    private static final EntityType[] TRACKED_TYPES = new EntityType[] {
        EntityType.ACCESS_SPECIFIER, EntityType.ANNOTATION,
        EntityType.DECLARATION_ELEMENT, EntityType.EXPR, EntityType.FIELD,
        EntityType.METHOD, EntityType.MODIFIERS, EntityType.PACKAGE,
        EntityType.SIGNATURE, EntityType.STATEMENT, EntityType.TYPE, };

    private final ASTTraverseTable traverseTable;
    private final ProjectTraverser projectTraverser;

    private final Map<EntityType, Collection<ASTNode>> astNodeStorage;
    private final Map<EntityType, Collection<ITypeBinding>> typeBindingStorage;
    private final Map<EntityType, Collection<ISynthesizedEntity>> synthesizedStorage;
    private final Map<ASTNode, ASTNode> desugaredToNearestNode;
    private final Map<ASTNode, ASTNode> pseudoParentTable;

    private final MultiDictionary<BindingKeyValue, MethodInvocation> methodInvocations;
    private final List<Expression> invocationsAndNames;
    private final List<Assignment> assignments;
    private final List<Pair<Expression, ? extends IBinding>> initializers;
    private final List<Expression> argumentsPassed;
    private final List<Expression> valuesReturned;
    private final Map<String, AbstractTypeDeclaration> typeDefinitionKeyLookup;
    private final Map<String, MethodDeclaration> methodBindingKeyLookup;

    public EntityDataBase(IProject project) {
        this.traverseTable = new ASTTraverseTable();
        this.projectTraverser = new ProjectTraverser(project, PROGRESS_MESSAGE);

        this.astNodeStorage = newEntityTypeMap();
        this.typeBindingStorage = newEntityTypeMap();
        this.synthesizedStorage = newEntityTypeMap();
        this.desugaredToNearestNode = new ReferenceMap<ASTNode, ASTNode>(WEAK, WEAK);
        this.pseudoParentTable = new ReferenceMap<ASTNode, ASTNode>(WEAK, WEAK);

        this.methodInvocations = MultiDictionary.newInstance();
        this.invocationsAndNames = Lists.newArrayList();
        this.assignments = Lists.newArrayList();
        this.initializers = Lists.newArrayList();
        this.argumentsPassed = Lists.newArrayList();
        this.valuesReturned = Lists.newArrayList();

        this.typeDefinitionKeyLookup = Maps.newHashMap();
        this.methodBindingKeyLookup = Maps.newHashMap();

        // We need to keep only one unique instance of each package found; we will
        // need to avoid the creation of packages for the moment, because renaming
        // packages or moving them is not a short to medium term term goal

        for (EntityType type : TRACKED_TYPES) {
            astNodeStorage.put(type, new ArrayList<ASTNode>());
            typeBindingStorage.put(type, new ArrayList<ITypeBinding>());
            synthesizedStorage.put(type, new ArrayList<ISynthesizedEntity>());
        }
    }

    private static <T> Map<EntityType, T> newEntityTypeMap() {
        return new EnumMap<EntityType, T>(EntityType.class);
    }

    public void populate() {
        projectTraverser.runTraversal(new ProjectTraverser.ICompilationUnitVisitor() {
            public @Override
            void visitCompilationUnit(CompilationUnit compilationUnit) {
                try {
                    EntityDataBase.pushCurrentDataBase(EntityDataBase.this);
                    IASTVisitor visitor = new EntityDataBaseVisitor();
                    traverseTable.traverseAST(compilationUnit, visitor);
                }
                finally {
                    EntityDataBase.popMostRecentDataBase();
                }
            }
        });

        if (false && DEBUG) {
            System.out.printf("All types used:%n");
            for (ITypeBinding typeBinding : typeBindingStorage.get(EntityType.TYPE)) {
                System.out.printf("%s%n", Entity.getQualifiedName(typeBinding));
            }
            System.out.printf("All types defined:%n");
            for (ASTNode node : astNodeStorage.get(EntityType.TYPE)) {
                System.out.printf("%s%n", Entity.getDisplayString(node));
            }
        }
    }

    public BindingsSet immeditateMatchingBinding(PatternExpression patternExpr,
        EntityType type, BindingMap in, IEntityLookup lookup, TypeLookupTable types,
        Object entity)
    {
        try {
            EntityDataBase.pushCurrentDataBase(this);
            final ProgramFragmentFactory builder;
            final List<ProgramFragment> fragments;
            Set<String> vars = patternExpr.getArcumVariableReferences();

            IEntityLookup masked = new MaskedLookup(lookup, vars);
            builder = new ProgramFragmentFactory(patternExpr, type, masked, types, true);
            fragments = builder.getAbstractProgramFragments();
            BindingsSet result = BindingsSet.newEmptySet();
            eachFragment: for (ProgramFragment fragment : fragments) {
                BindingMap theta = fragment.matches(entity);
                if (theta == null) {
                    continue eachFragment;
                }
                boolean allVarsMatch = true;
                varsMatch: for (String var : vars) {
                    if (var.equals(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE)) {
                        continue varsMatch;
                    }
                    Object startedWith = in.lookupEntity(var);
                    if (startedWith != null) {
                        Object found = theta.lookupEntity(var);
                        if (DEBUG) {
                            System.out.printf("Compare: %s with %s%n", //->
                                ASTUtil.getDebugString(startedWith), //->
                                ASTUtil.getDebugString(found));
                        }
                        if (startedWith != found) {
                            allVarsMatch = false;
                        }
                    }
                }
                if (allVarsMatch) {
                    BindingMap merge = theta.consistentMerge(in);
                    if (merge != null) {
                        result.addEntry(merge);
                    }
                }
            }
            return result;
        }
        finally {
            popMostRecentDataBase();
        }
    }

    public BindingsSet enumerateMatchingBindings(PatternExpression patternExpr,
        EntityType type, BindingMap in, IEntityLookup lookup, TypeLookupTable types)
    {
        try {
            EntityDataBase.pushCurrentDataBase(this);
            final ProgramFragmentFactory builder;
            final List<ProgramFragment> fragments;

            builder = new ProgramFragmentFactory(patternExpr, type, lookup, types, true);
            fragments = builder.getAbstractProgramFragments();

            BindingsSet literalMatches = immediateLiteralMatches(type, in, lookup,
                fragments);
            if (literalMatches != null) {
                return literalMatches;
            }

            if (EntityType.TYPE.isAssignableFrom(type)) {
                Collection<ITypeBinding> typeBindings = typeBindingStorage.get(type);
                return entitySearch(typeBindings, fragments, in);
            }
            else if (EntityType.SIGNATURE.isAssignableFrom(type)) {
                Collection<ISynthesizedEntity> sigs = synthesizedStorage.get(type);
                return entitySearch(sigs, fragments, in);
            }
            else if (EntityType.MODIFIERS.isAssignableFrom(type)) {
                Collection<ISynthesizedEntity> lists = synthesizedStorage.get(type);
                ArcumError.fatalError("There's a use case for this %s?", lists);
                return null;
            }
            else {
                Collection<ASTNode> astNodes = astNodeStorage.get(type);
                return entitySearch(astNodes, fragments, in);
            }
        }
        finally {
            popMostRecentDataBase();
        }
    }

    // EXAMPLE: These two push/pop methods are examples of a @StackWinding idiom,
    // where each block that the push is called must have a finally at the end that
    // calls the unwind operation. Naturally, this limits how you can use the API,
    // but that's the point. TODO: statement matching for this kind of thing, and
    // in the bigger picture we need to think about how several of these would
    // compose (e.g., for the moment each one used would require a new try/finally,
    // potentially nested in ones that already exist).
    public static void pushCurrentDataBase(EntityDataBase entityDataBase) {
        currentEDB.push(entityDataBase);
    }

    public static void popMostRecentDataBase() {
        currentEDB.pop();
    }

    // If the fragments are already resolved then we don't need to search for them:
    // we can just return them as is.
    private BindingsSet immediateLiteralMatches(EntityType type, BindingMap in,
        IEntityLookup lookup, List<ProgramFragment> fragments)
    {
        if (fragments.size() != 1) {
            return null;
        }
        ProgramFragment fragment = fragments.get(0);
        if (EntityType.TYPE.isAssignableFrom(type)) {
            if (fragment instanceof ResolvedType) {
                ResolvedType resolvedType = (ResolvedType)fragment;
                BindingMap theta;
                theta = resolvedType.matches(resolvedType.getTypeBinding());
                if (theta != null) {
                    BindingMap merge = theta.consistentMerge(in);
                    if (merge != null) {
                        return BindingsSet.newSet(merge);
                    }
                }
            }
        }
        else if (EntityType.SIGNATURE.isAssignableFrom(type)) {
            if (fragment instanceof PartialNode) {
                AST ast = AST.newAST(AST.JLS3);
                BindingMap node = fragment.generateNode(lookup, ast);
                MethodDeclaration methodDecl = (MethodDeclaration)node.getResult();
                SignatureEntity entity = new SignatureEntity(methodDecl);
                BindingMap merge = new BindingMap(entity);
                merge = merge.consistentMerge(in);
                return BindingsSet.newSet(merge);
            }
        }
        return null;
    }

    private <T> BindingsSet entitySearch(Collection<T> entities,
        Collection<ProgramFragment> fragments, BindingMap in)
    {
        final BindingsSet result = BindingsSet.newEmptySet();
        entitySearch: for (T entity : entities) {
            for (ProgramFragment fragment : fragments) {
                BindingMap theta = fragment.matches(entity);
                if (theta != null) {
                    BindingMap merge = theta.consistentMerge(in);
                    if (merge != null) {
                        result.addEntry(merge);
                        continue entitySearch;
                    }
                }
            }
        }
        return result;
    }

    // May return null
    public AbstractTypeDeclaration lookupTypeDeclaration(ITypeBinding givenBinding) {
        String key = givenBinding.getKey();
        AbstractTypeDeclaration typeDecl = typeDefinitionKeyLookup.get(key);
        return typeDecl;
    }

    public static AbstractTypeDeclaration findTypeDeclaration(ITypeBinding givenBinding) {
        EntityDataBase edb = currentEDB.peek();
        return edb.lookupTypeDeclaration(givenBinding);
    }

    public ITypeBinding lookupTypeBinding(AbstractTypeDeclaration node) {
        ITypeBinding result = resolveBinding(node);
        return result;
    }

    // Collect a database of program fragments seen, with some canonicalization
    // applied to them in order to simplify matching. E.g.,
    //   static final int a = 1, b[] = null, c[][];
    // gets turned into three entries:
    //   static final int a = 1;
    //   static final int[] b = null;
    //   static final int[][] c;
    // And,
    //   int a[];
    // gets turned into:
    //   int[] a;
    private class EntityDataBaseVisitor extends ASTVisitorAdaptor
    {
        @Override
        public boolean visitASTNode(ASTNode node, StructuralPropertyDescriptor edge) {
            if (node == null) {
                return false;
            }
            else if (node instanceof PackageDeclaration) {
                return handlePackageDeclaration((PackageDeclaration)node);
            }
            else if (node instanceof ImportDeclaration) {
                return handleImportDeclaration((ImportDeclaration)node);
            }
            else if (node instanceof Modifier) {
                return handleModifier((Modifier)node, edge);
            }
            else if (node instanceof Annotation) {
                return handleAnnotation((Annotation)node);
            }
            else if (node instanceof AbstractTypeDeclaration) {
                return handleTypeDeclaration((AbstractTypeDeclaration)node);
            }
            else if (node instanceof VariableDeclaration) {
                return handleVariableDeclaration((VariableDeclaration)node);
            }
            else if (node instanceof VariableDeclarationStatement) {
                return handleVariableDeclarationStatement((VariableDeclarationStatement)node);
            }
            else if (node instanceof MethodInvocation) {
                return handleMethodInvocation((MethodInvocation)node);
            }
            else if (node instanceof Expression) {
                return handleExpression((Expression)node);
            }
            else if (node instanceof FieldDeclaration) {
                return handleFieldDeclaration((FieldDeclaration)node);
            }
            else if (node instanceof MethodDeclaration) {
                return handleMethod((MethodDeclaration)node);
            }
            else if (node instanceof Statement) {
                return handleStatement(node);
            }
            else if (node instanceof Initializer) {
                return handleInitializer((Initializer)node);
            }
            return true;
        }

        private boolean handleImportDeclaration(ImportDeclaration node) {
            // We don't handle or match against imports: The idea being that we
            // desugar over uses of them.
            return false;
        }

        private boolean handleAnnotation(Annotation node) {
            // ANNOTATION
            storeASTNode(EntityType.ANNOTATION, node);
            // MONDAY: Annotations can be embedded in other annotations and we
            // may not always be matching or generating the top level one.
            return false;
        }

        private boolean handleTypeDeclaration(AbstractTypeDeclaration atd) {
            // TYPE
            ITypeBinding binding = atd.resolveBinding();
            typeDefinitionKeyLookup.put(binding.getKey(), atd);
            storeASTNode(EntityType.TYPE, atd);
            storeTypeBinding(binding);
            return true;
        }

        // Handle a single, local variable
        private boolean handleVariableDeclaration(VariableDeclaration node) {
            // DECLARATION_ELEMENT (part 1: formals)
            int dims = node.getExtraDimensions();
            if (dims == 0) {
                // Just visit initializer expression, if present
                final Expression initializer;
                if (node instanceof SingleVariableDeclaration) {
                    SingleVariableDeclaration decl = (SingleVariableDeclaration)node;
                    initializer = decl.getInitializer();
                }
                else if (node instanceof VariableDeclarationFragment) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment)node;
                    initializer = fragment.getInitializer();
                }
                else {
                    initializer = null;
                }
                if (initializer != null) {
                    reentrantVisit(initializer);
                }
            }
            else {
                // then we need to desugar the array
                AST ast = node.getAST();
                SimpleName name = node.getName();

                if (node instanceof SingleVariableDeclaration) {
                    SingleVariableDeclaration decl = (SingleVariableDeclaration)node;
                    Type baseType = decl.getType();
                    List modsAndAnnots = decl.modifiers();
                    SingleVariableDeclaration svd = ast.newSingleVariableDeclaration();
                    svd.setType(createDerivedType(ast, baseType, dims));
                    svd.setName(Entity.copySubtree(ast, name));
                    Expression initializer = decl.getInitializer();
                    if (initializer != null) {
                        svd.setInitializer(Entity.copySubtree(ast, initializer));
                        reentrantVisit(initializer);
                    }
                    svd.modifiers().addAll(modsAndAnnots);
                    node = svd;
                }
                else if (node instanceof VariableDeclarationFragment) {
                    VariableDeclarationFragment fragment = (VariableDeclarationFragment)node;
                    node = duplicateFragment(ast, fragment);
                }
            }
            // we must get the type binding from the name, in order to pick
            // up any extra dimensions if they exist
            storeTypeBindingFromName(node.getName());

            for (Annotation annotation : ASTUtil.getAnnotations(node)) {
                handleAnnotation(annotation);
                associateNodeToPseudoParent(annotation, node);
            }

            associateBindingToDeclarationElement(node.resolveBinding(), node);
            storeASTNode(EntityType.DECLARATION_ELEMENT, node);
            handleVariableInitialization(node);
            return false;
        }

        private boolean handleVariableDeclarationStatement(VariableDeclarationStatement varDeclStmt) {
            // DECLARATION_ELEMENT (part 2: locals)
            AST ast = varDeclStmt.getAST();
            List fragments = varDeclStmt.fragments();
            Type baseType = varDeclStmt.getType();
            List<Annotation> annotations = ASTUtil.getAnnotations(varDeclStmt);

            for (Annotation annotation : annotations) {
                handleAnnotation(annotation);
            }

            boolean needToDesugar = true;
            if (fragments.size() == 1) {
                needToDesugar = false;
                for (Annotation annotation : annotations) {
                    associateNodeToPseudoParent(annotation, varDeclStmt);
                }
                VariableDeclarationFragment frag = (VariableDeclarationFragment)fragments
                    .get(0);
                associateBindingToDeclarationElement(frag.resolveBinding(), varDeclStmt);
                storeASTNode(EntityType.DECLARATION_ELEMENT, varDeclStmt);
                handleVariableInitialization(varDeclStmt);
            }

            // There may be several variable declarations here, but all will
            // share the same modifiers and base type (but some may be array
            // types derived from the base type) -- canonicalize by making
            // a new VariableDeclarationStatement for each
            for (Object obj : fragments) {
                VariableDeclarationFragment frag = (VariableDeclarationFragment)obj;
                storeTypeBindingFromName(frag.getName());

                if (needToDesugar) {
                    VariableDeclarationFragment newFrag = duplicateFragment(ast, frag);

                    VariableDeclarationStatement newStmt;
                    newStmt = ast.newVariableDeclarationStatement(newFrag);
                    newStmt.setType(createDerivedType(ast, baseType, frag
                        .getExtraDimensions()));

                    List modifiers = ASTNode.copySubtrees(ast, varDeclStmt.modifiers());
                    newStmt.modifiers().addAll(modifiers);

                    associateBindingToDeclarationElement(frag.resolveBinding(), newStmt);
                    ASTUtil.recordUpdatedNode(varDeclStmt, newStmt);
                    storeDesugaredASTNode(EntityType.DECLARATION_ELEMENT, newStmt, frag);
                    handleVariableInitialization(frag);
                    for (Annotation annotation : annotations) {
                        associateNodeToPseudoParent(annotation, newStmt);
                    }
                }
                else {
                    Expression initializer = frag.getInitializer();
                    if (initializer != null) {
                        reentrantVisit(initializer);
                    }
                }
            }
            // TUESDAY: Lots of other cases for DECLARATION_ELEMENT, like return types,
            // typecasts. But what about super classes, super interfaces,
            // throws clauses, etc...?
            return false;
        }

        private boolean handleMethodInvocation(MethodInvocation invocation) {
            storeASTNode(EntityType.EXPR, invocation);
            Expression targetExpr = invocation.getExpression();
            if (targetExpr != null) {
                reentrantVisit(targetExpr);
            }
            else {
                // VERSION2: We need to create a desugared "this" or class name
                // as the target and add it: however, the entire method invocation
                // expression must match, so adding this alone won't fix everything
                // unless we change the method invocation itself: a major change that
                // could greatly increase (close to double due to method bodies?)
                // the size of the AST -- an alternative solution is to abstract
                // away everything we need from the AST: E.g. keep a text copy of
                // each source file visited and for each entity we need only a
                // reference to the file's name, the starting position, the length,
                // and the resolved type of it (if applicable). Ideally we can
                // represent resolved types in a way lends to easy comparison (like
                // is-A) without having to keep the type resolutions of any of the
                // ASTs around.
            }
            List arguments = invocation.arguments();
            for (Object obj : arguments) {
                Expression argument = (Expression)obj;
                reentrantVisit(argument);
                argumentsPassed.add(argument);
            }

            // MONDAY: Also need to check ClassInstanceCreation, SuperMethodInvocation,
            // and potentially other ways to invoke methods
            IMethodBinding binding = invocation.resolveMethodBinding();
            BindingKeyValue key = BindingKeyValue.newInstance(EntityType.METHOD, binding);
            methodInvocations.addDefinition(key, invocation);
            invocationsAndNames.add(invocation);
            return false;
        }

        private boolean handleExpression(Expression node) {
            // EXPR
            storeASTNode(EntityType.EXPR, node);

            if (node instanceof Assignment) {
                Assignment assignment = (Assignment)node;
                assignments.add(assignment);
            }

            if (node instanceof Name) {
                Name name = (Name)node;
                IBinding binding = resolveBindingNullOK(name);
                if (binding != null) {
                    if (binding.getKind() == IBinding.VARIABLE) {
                        invocationsAndNames.add(name);
                    }
                }
                else {
                    if (!ASTUtil.isLabel(name)) {
                        ArcumError.fatalError("Assertion failed: A non-label is a name"
                            + " without a binding (%s in %s)", //->
                            StringUtil.debugDisplay(name), //->
                            StringUtil.debugDisplay(name.getParent()));
                    }
                }
            }

            if (node instanceof QualifiedName) {
                // E.g., The aim is for expressions like:
                //   treeNode.left.left.right.left = null
                // to be entered as one field assignment and three field accesses.
                // MACNEIL: These may need to be ensugared as FieldAccess nodes, when
                // applicable. Right now, they are ensugared at a later point
                QualifiedName qualifiedName = (QualifiedName)node;
                Name qualifier = qualifiedName.getQualifier();
                if (qualifier instanceof QualifiedName) {
                    IBinding binding = qualifier.resolveBinding();
                    if (binding.getKind() == IBinding.VARIABLE) {
                        IVariableBinding var = (IVariableBinding)binding;
                        if (var.isField()) {
                            handleExpression(qualifier);
                        }
                    }
                }
                return false;
            }
            else if (node instanceof FieldAccess) {
                FieldAccess fieldAccess = (FieldAccess)node;
                Expression expression = fieldAccess.getExpression();
                reentrantVisit(expression);
                invocationsAndNames.add(fieldAccess);
                return false;
            }

            return true;
        }

        private boolean handleFieldDeclaration(final FieldDeclaration fieldDecl) {
            // FIELD, and DECLARATION_ELEMENT (part 3: fields)
            AST ast = fieldDecl.getAST();
            List fragments = fieldDecl.fragments();
            Type baseType = fieldDecl.getType();

            boolean needToDesugar = true;
            if (fragments.size() == 1) {
                needToDesugar = false;
                VariableDeclarationFragment frag = (VariableDeclarationFragment)fragments
                    .get(0);
                associateBindingToDeclarationElement(frag.resolveBinding(), fieldDecl);
                storeASTNode(EntityType.DECLARATION_ELEMENT, fieldDecl);
                storeASTNode(EntityType.FIELD, fieldDecl);
                handleVariableInitialization(fieldDecl);
            }

            // As with the VariableDeclarationStatement case, which has similar
            // code, there may be several declarations here
            for (Object obj : fragments) {
                VariableDeclarationFragment frag = (VariableDeclarationFragment)obj;
                storeTypeBindingFromName(frag.getName());

                if (needToDesugar) {
                    VariableDeclarationFragment newFrag = duplicateFragment(ast, frag);

                    FieldDeclaration newFieldDecl;
                    newFieldDecl = ast.newFieldDeclaration(newFrag);
                    int extraDimensions = frag.getExtraDimensions();
                    Type derivedType = createDerivedType(ast, baseType, extraDimensions);
                    newFieldDecl.setType(derivedType);

                    // we need a fresh copy of the modifiers for each newFieldDecl
                    List modifiers = ASTNode.copySubtrees(ast, fieldDecl.modifiers());
                    newFieldDecl.modifiers().addAll(modifiers);

                    associateBindingToDeclarationElement(frag.resolveBinding(),
                        newFieldDecl);
                    ASTUtil.recordUpdatedNode(fieldDecl, newFieldDecl);
                    storeDesugaredASTNode(EntityType.DECLARATION_ELEMENT, newFieldDecl,
                        frag);
                    storeDesugaredASTNode(EntityType.FIELD, newFieldDecl, frag);
                    handleVariableInitialization(frag);
                }
                else {
                    Expression initializer = frag.getInitializer();
                    if (initializer != null) {
                        reentrantVisit(initializer);
                    }
                }
            }
            return false;
        }

        // METHOD
        private boolean handleMethod(MethodDeclaration methodDecl) {
            // TODO: Desugar extra array dimensions
            storeASTNode(EntityType.METHOD, methodDecl);
            storeSignatureEntity(EntityType.SIGNATURE, new SignatureEntity(methodDecl));

            IMethodBinding methodBinding = methodDecl.resolveBinding();
            methodBindingKeyLookup.put(methodBinding.getKey(), methodDecl);

            Type returnType = methodDecl.getReturnType2();
            if (returnType != null) {
                associateBindingToDeclarationElement(methodDecl.resolveBinding(),
                    returnType);
                storeASTNode(EntityType.DECLARATION_ELEMENT, returnType);
                for (Annotation annotation : ASTUtil.getAnnotations(methodDecl)) {
                    associateNodeToPseudoParent(annotation, returnType);
                }
            }

            return true;
        }

        // PACKAGE
        private boolean handlePackageDeclaration(PackageDeclaration node) {
            // Some packages may be missing if there are no Java source files
            // directly defined in them (e.g., when it has sub packages). This
            // is a low priority concern, however
            PackageDeclaration packageDeclaration = (PackageDeclaration)node;
            storeASTNode(EntityType.PACKAGE, packageDeclaration);
            List annotations = packageDeclaration.annotations();
            // don't look further down, otherwise the qualified name in the
            // package name will be treated like an expression, which it isn't
            // -- instead, grab the annotations directly
            for (Object obj : annotations) {
                Annotation annotation = (Annotation)obj;
                visitASTNode(annotation, PackageDeclaration.ANNOTATIONS_PROPERTY);
            }
            return false;
        }

        private boolean handleInitializer(Initializer node) {
            // MACNEIL: WE SKIP THESE FOR NOW!
            return false;
        }

        // STATEMENT
        private boolean handleStatement(ASTNode node) {
            storeASTNode(EntityType.STATEMENT, node);

            if (node instanceof ReturnStatement) {
                ReturnStatement returnStmt = (ReturnStatement)node;
                Expression expression = returnStmt.getExpression();
                if (expression != null) {
                    valuesReturned.add(expression);
                }
            }

            return true;
        }

        // ACCESS_SPECIFIER and MODIFIERS
        private boolean handleModifier(Modifier node, StructuralPropertyDescriptor edge) {
            // These are handled on an edge basis as a unit, so we shouldn't
            // reach here unless there was an edge type that was forgotten
            ArcumError.fatalError("AND %s IS AN EDGE THAT HOLDS MODIFIERS%n", edge);
            return false;
        }

        @Override
        public boolean beforeVisitEdge(ASTNode parent, StructuralPropertyDescriptor edge)
        {
            if (Entity.isModifiersEdge(edge)) {
                List modsAndAnnots = (List)parent.getStructuralProperty(edge);
                EntityList accessSpecifier = EntityList.newModifiersList();
                EntityList modifiersList = EntityList.newModifiersList();
                for (Object modOrAnnot : modsAndAnnots) {
                    if (modOrAnnot instanceof Annotation) {
                        visitASTNode((Annotation)modOrAnnot, edge);
                    }
                    else if (modOrAnnot instanceof Modifier) {
                        ModifierElement element;
                        element = ModifierElement.lookup((Modifier)modOrAnnot);

                        modifiersList.addEntity(element, null);
                        if (element.isAccessSpecifier()) {
                            accessSpecifier.addEntity(element, null);
                        }
//                      else {
//                          modifiersList.addEntity(element);
//                          modifiersList.addRequiredModifier(element);
//                          hasNonAccessSpecModifier = true;
//                      }
                    }
                }
                if (accessSpecifier.isEmpty()) {
                    accessSpecifier.addEntity(ModifierElement.MOD_PACKAGE, null);
                }
                storeModifiersList(EntityType.ACCESS_SPECIFIER, accessSpecifier);
                storeModifiersList(EntityType.MODIFIERS, modifiersList);
//            store(EntityType.ACCESS_SPECIFIER, accessSpecifier, parent);
//            if (hasNonAccessSpecModifier) {
//                store(EntityType.MODIFIERS, modifiersList, parent);
//            }
                return false;
            }
            else if (edge == TypeDeclaration.NAME_PROPERTY
                || edge == EnumDeclaration.NAME_PROPERTY
                || edge == AnnotationTypeDeclaration.NAME_PROPERTY
                || edge == MethodDeclaration.NAME_PROPERTY)
            {
                // don't traverse the name properly of a type or method declaration,
                // because it's not actually an expression
                return false;
            }
            return super.beforeVisitEdge(parent, edge);
        }

        // Given a base type and the array type implied by the given fragment, return
        // a derived type to be used instead
        private Type createDerivedType(AST ast, Type baseType, int dimensions) {
            Type newType = Entity.copySubtree(ast, baseType);
            for (int i = 0; i < dimensions; ++i) {
                newType = ast.newArrayType(newType);
            }
            return newType;
        }

        // Copy the given declaration fragment, but not its array dimensions. Also,
        // recursively visit the init expression, if present
        private VariableDeclarationFragment duplicateFragment(AST ast,
            VariableDeclarationFragment frag)
        {
            VariableDeclarationFragment newFrag = ast.newVariableDeclarationFragment();

            SimpleName name = frag.getName();
            name = ast.newSimpleName(name.getIdentifier());
            newFrag.setName(name);

            Expression initializer = frag.getInitializer();
            if (initializer != null) {
                initializer = Entity.copySubtree(ast, initializer);
                newFrag.setInitializer(initializer);
                reentrantVisit(initializer);
            }

            return newFrag;
        }

        // Fully traverse the given node. E.g., an init expression may have anonymous inner classes complete
        // with methods and fields and other code
        private void reentrantVisit(ASTNode node) {
            traverseTable.traverseAST(node, this);
        }

        // TODO: See if this would be useful anywhere else: Is the method typeof
        // semantics coded anywhere else?
//        // given a name it returns the type of the name, given a method, returns
//        // the return type of the method
//        private ITypeBinding getTypeBinding(IBinding binding) {
//            if (binding instanceof ITypeBinding) {
//                return (ITypeBinding)binding;
//            }
//            else if (binding instanceof IVariableBinding) {
//                IVariableBinding variableBinding = (IVariableBinding)binding;
//                return variableBinding.getType();
//            }
//            else if (binding instanceof IMethodBinding) {
//                IMethodBinding methodBinding = (IMethodBinding)binding;
//                return methodBinding.getReturnType();
//            }
//            return null;
//        }

        // Note: This may be called multiple times for the same ASTNode but using
        // different types. The most specific type should be used last.
        private void storeASTNode(EntityType type, ASTNode node) {
            entityTypeTable.put(node.getClass(), type);
            astNodeStorage.get(type).add(node);
        }

        private void storeTypeBinding(ITypeBinding typeBinding) {
            typeBindingStorage.get(EntityType.TYPE).add(typeBinding);
        }

        private void storeTypeBindingFromName(SimpleName name) {
            ITypeBinding typeBinding = Entity.getTypeOf(name);
            IBinding binding = name.resolveBinding();
            if (binding == null && typeBinding == null) {
                System.err
                    .printf("No information for %s in %s%n", name, name.getParent());
                return;
            }
            storeTypeBinding(typeBinding);
        }

        private void storeDesugaredASTNode(EntityType type, ASTNode entityValue,
            ASTNode nearestNode)
        {
            storeASTNode(type, entityValue);
            desugaredToNearestNode.put(entityValue, nearestNode);
        }

        private void storeModifiersList(EntityType type, EntityList modifiersList) {
            synthesizedStorage.get(type).add(modifiersList);
        }

        private void storeSignatureEntity(EntityType type, SignatureEntity signatureEntity)
        {
            synthesizedStorage.get(type).add(signatureEntity);
        }
    }

    // inserts into the given table all trait values for the built-in predicates:
    // hasField, hasMethod
    public void insertBuiltInTraitValues(OptionMatchTable table) {
        try {
            pushCurrentDataBase(this);

            insertOwnerRelation(table, EntityType.FIELD, "hasField", astNodeStorage);
            insertOwnerRelation(table, EntityType.METHOD, "hasMethod", astNodeStorage);
            insertDirectParentOfRelation(table, EntityType.ANNOTATION, "hasAnnotation",
                astNodeStorage);

            insertInvokesRelation(table, methodInvocations);
            insertHasInvocationTargetRelation(table, methodInvocations);
            insertDeclaredByRelation(table);
            insertCopiedToRelation(table);

            // MACNEIL : If we consider all types in the program, and not all types
            // defined in the project, we should think of a different strategy, one which
            // may require a full search of the jars on the path for all matching types
            insertSuperclassRelation(table, astNodeStorage.get(EntityType.TYPE));
        }
        finally {
            popMostRecentDataBase();
        }
    }

    private <T> void insertOwnerRelation(OptionMatchTable table, EntityType entityType,
        String traitName, Map<EntityType, Collection<T>> lookupTable)
    {
        final TraitSignature type;
        final List<String> names;
        final Collection<T> nodes;

        type = BUILT_IN_TRAIT_TYPES.get(traitName);
        names = Lists.transform(type.getFormals(), FormalParameter.getIdentifier);
        nodes = lookupTable.get(entityType);

        table.addBuiltInTrait(type);
        for (T node : nodes) {
            ITypeBinding definingType = findDefiningType(node);
            Map<String, Object> values = values(names, definingType, node);
            EntityTuple instance = new EntityTuple(type, values, null);
            table.addTraitInstance(traitName, instance);
        }
    }

    private <T extends ASTNode> void insertDirectParentOfRelation(OptionMatchTable table,
        EntityType entityType, String traitName,
        Map<EntityType, Collection<T>> lookupTable)
    {
        final TraitSignature type;
        final List<String> names;
        final Collection<T> nodes;

        type = BUILT_IN_TRAIT_TYPES.get(traitName);
        names = Lists.transform(type.getFormals(), FormalParameter.getIdentifier);
        nodes = lookupTable.get(entityType);

        table.addBuiltInTrait(type);
        for (T node : nodes) {
            ASTNode parent = lookupPseudoParent(node);
            Map<String, Object> values = values(names, parent, node);
            EntityTuple instance = new EntityTuple(type, values, null);
            table.addTraitInstance(traitName, instance);

            if (DEBUG) {
                System.out.printf("hasAnnotation info: %s on %s%n", node, StringUtil
                    .firstLine(parent.toString()));
            }
        }
    }

    private @ReadWriteAccess(@MethodGroup(type = EntityDataBase.class, names = {
        "associateBindingToDeclarationElement", "lookupDeclarationElement" }))
    final Map<String, Object> declarationElementLookup = Maps.newHashMap();

    private void associateBindingToDeclarationElement(IBinding binding, @Union("Entity")
    Object declarationElement)
    {
        String key = binding.getKey();
        declarationElementLookup.put(key, declarationElement);
    }

    private Object lookupDeclarationElement(IBinding binding) {
        if (binding instanceof IVariableBinding) {
            // work with the binding in the generic type instead of an instance of
            // the generic type
            binding = ((IVariableBinding)binding).getVariableDeclaration();
        }
        String key = binding.getKey();
        Object result = declarationElementLookup.get(key);
        return result;
    }

    private void associateNodeToPseudoParent(ASTNode node, ASTNode parent) {
        pseudoParentTable.put(node, parent);
    }

    private ASTNode lookupPseudoParent(ASTNode node) {
        if (pseudoParentTable.containsKey(node)) {
            return pseudoParentTable.get(node);
        }
        else {
            return node.getParent();
        }
    }

    // TODO: Need to worry about method overriding too: The methodKey used should
    // actually be a set of method keys: That method itself, and all methods that
    // override it.
    private void insertInvokesRelation(OptionMatchTable table,
        MultiDictionary<BindingKeyValue, MethodInvocation> methodInvocations)
    {
        final String INVOKES = "invokes";

        TraitSignature type = BUILT_IN_TRAIT_TYPES.get(INVOKES);
        List<String> names = Lists.transform(type.getFormals(), getIdentifier);

        table.addBuiltInTrait(type);
        for (Entry<BindingKeyValue, List<MethodInvocation>> keyedDefinitions : methodInvocations)
        {
            BindingKeyValue methodKey = keyedDefinitions.getKey();
            for (MethodInvocation expr : keyedDefinitions.getValue()) {
                Map<String, Object> values = values(names, expr, methodKey);
                EntityTuple instance = new EntityTuple(type, values, null);
                table.addTraitInstance(INVOKES, instance);

                IMethodBinding caller = ASTUtil.getDefiningMethod(expr);
                if (caller != null) {
                    BindingKeyValue callerKey = BindingKeyValue.newInstance(
                        EntityType.METHOD, caller);
                    values = values(names, callerKey, methodKey);
                    instance = new EntityTuple(type, values, null);
                    table.addTraitInstance(INVOKES, instance);
                }
            }
        }
    }

    private void insertHasInvocationTargetRelation(OptionMatchTable table,
        MultiDictionary<BindingKeyValue, MethodInvocation> methodInvocations)
    {
        final String HAS_INVOCATION_TARGET = "hasInvocationTarget";

        TraitSignature type = BUILT_IN_TRAIT_TYPES.get(HAS_INVOCATION_TARGET);
        List<String> names = Lists.transform(type.getFormals(), getIdentifier);

        table.addBuiltInTrait(type);
        for (Entry<BindingKeyValue, List<MethodInvocation>> keyedDefinitions : methodInvocations)
        {
            for (MethodInvocation invocation : keyedDefinitions.getValue()) {
                Expression target = invocation.getExpression();
                if (target == null) {
                    target = invocation.getAST().newThisExpression();
                }
                Map<String, Object> values = values(names, invocation, target);
                EntityTuple instance = new EntityTuple(type, values, null);
                table.addTraitInstance(HAS_INVOCATION_TARGET, instance);
            }
        }
    }

    // Where declarationReferences is a list of method invocation expressions and
    // all variable reference expressions (locals, parameters, fields).
    // TODO: implement ArrayAccess as a declaration reference, very similar to
    // accessing a field. However, fields can have annotations, while the type of
    // an array cannot.
    private void insertDeclaredByRelation(OptionMatchTable table) {
        final String DECLARED_BY = "declaredBy";

        TraitSignature type = BUILT_IN_TRAIT_TYPES.get(DECLARED_BY);
        List<String> names = Lists.transform(type.getFormals(), getIdentifier);

        table.addBuiltInTrait(type);
        for (Expression reference : invocationsAndNames) {
            IBinding declarationBinding = getDeclarationBinding(reference);
            Object declarationElement = lookupDeclarationElement(declarationBinding);
            if (declarationElement != null) {
                Map<String, Object> values = values(names, reference, declarationElement);
                EntityTuple instance = new EntityTuple(type, values, null);
                table.addTraitInstance(DECLARED_BY, instance);
            }
        }
    }

    private void insertCopiedToRelation(OptionMatchTable table) {
        final String COPIED_TO = "copiedTo";

        TraitSignature type = BUILT_IN_TRAIT_TYPES.get(COPIED_TO);
        List<String> names = Lists.transform(type.getFormals(), getIdentifier);

        List<Map<String, Object>> listOfValues = Lists.newArrayList();

        table.addBuiltInTrait(type);
        for (Expression valueCopied : argumentsPassed) {
            MethodInvocation methodCall = (MethodInvocation)valueCopied.getParent();
            String methodBindingKey = methodCall.resolveMethodBinding().getKey();
            MethodDeclaration methodDecl = methodBindingKeyLookup.get(methodBindingKey);
//            if (valueCopied.toString().equals("stmtLookup")) {
//                System.out.printf("copiedTo %s %s%n", valueCopied, methodBindingKey);
//                for (String str : methodBindingKeyLookup.keySet()) {
//                    System.out.printf("%s%n", str);
//                }
//            }
            if (methodDecl != null) {
                IBinding declarationBinding = findMatchingParameterDeclaration(valueCopied, methodDecl);
                Object declElement = lookupDeclarationElement(declarationBinding);
                listOfValues.add(values(names, valueCopied, declElement));
            }
        }
        for (Assignment assignment : assignments) {
            Expression lhs = assignment.getLeftHandSide();
            Expression valueCopied = assignment.getRightHandSide();
            IBinding declarationBinding = getDeclarationBinding(lhs);
            Object declElement = lookupDeclarationElement(declarationBinding);
            if (declElement != null) {
                listOfValues.add(values(names, valueCopied, declElement));
            }
        }
        for (Pair<Expression, ? extends IBinding> initializer : initializers) {
            Expression valueCopied = initializer.getFirst();
            IBinding declarationBinding = initializer.getSecond();
            Object declElement = lookupDeclarationElement(declarationBinding);
            listOfValues.add(values(names, valueCopied, declElement));
        }
        for (Expression valueCopied : valuesReturned) {
            IMethodBinding methodBinding = ASTUtil.getDefiningMethod(valueCopied);
            Object declElement = lookupDeclarationElement(methodBinding);
            listOfValues.add(values(names, valueCopied, declElement));
        }

        for (Map<String, Object> values : listOfValues) {
            EntityTuple instance = new EntityTuple(type, values, null);
            table.addTraitInstance(COPIED_TO, instance);
        }
    }

    private IBinding findMatchingParameterDeclaration(Expression argument,
        MethodDeclaration methodDecl)
    {
        StructuralPropertyDescriptor spd = argument.getLocationInParent();
        ASTNode parent = argument.getParent();
        List arguments = (List)parent.getStructuralProperty(spd);
        int location = 0;
        for (int i=0; i<arguments.size(); ++i) {
            if (arguments.get(i) == argument) {
                location = i;
                break;
            }
        }
        boolean isVarargs = methodDecl.isVarargs();
        List parameters = methodDecl.parameters();
        SingleVariableDeclaration parameter = null;
        boolean isInVarArgList = false;
        if (location >= parameters.size()) {
            if (!isVarargs) {
                ArcumError.fatalError("Funny internal error, should be var args");
            }
            parameter = (SingleVariableDeclaration)parameters.get(parameters.size() - 1);
            isInVarArgList = true;
        }
        else {
            parameter = (SingleVariableDeclaration)parameters.get(location);
        }
        if (isInVarArgList) {
            // TODO: Need to work with the type 'Foo' directly, not 'Foo...'
        }
        return parameter.resolveBinding();
    }

    private IBinding getDeclarationBinding(Expression expr) {
        IBinding declarationBinding;
        if (expr instanceof Name) {
            Name name = (Name)expr;
            IBinding binding = name.resolveBinding();
            if (!(binding instanceof IVariableBinding))
                ArcumError
                    .fatalError("Not an instance of variable binding, time to rewrite");
            IVariableBinding varBinding = (IVariableBinding)binding;
            declarationBinding = varBinding.getVariableDeclaration();
        }
        else if (expr instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess)expr;
            declarationBinding = fieldAccess.resolveFieldBinding();
        }
        else if (expr instanceof ArrayAccess) {
            ArrayAccess arrayAccess = (ArrayAccess)expr;
            // WEDNESDAY: We un-array it for now, but this is not what we want to do
            declarationBinding = getDeclarationBinding(arrayAccess.getArray());
        }
        else if (expr instanceof MethodInvocation) {
            MethodInvocation invocation = (MethodInvocation)expr;
            declarationBinding = invocation.resolveMethodBinding();
        }
        else {
            declarationBinding = null;
            ArcumError.fatalError("Internal error found in getDeclarationBinding."
                + " What's the declaration of a %s?", StringUtil.debugDisplay(expr));
        }
        return declarationBinding;
    }

    private void handleVariableInitialization(ASTNode node) {
        if (node instanceof SingleVariableDeclaration) {
            SingleVariableDeclaration decl = (SingleVariableDeclaration)node;
            Expression initializer = decl.getInitializer();
            if (initializer != null) {
                initializers.add(newPair(initializer, decl.resolveBinding()));
            }
        }
        else if (node instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment)node;
            Expression initializer = fragment.getInitializer();
            if (initializer != null) {
                initializers.add(newPair(initializer, fragment.resolveBinding()));
            }
        }
        else if (node instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varDeclStmt = (VariableDeclarationStatement)node;
            List fragments = varDeclStmt.fragments();
            if (fragments.size() != 1) {
                ArcumError.fatalError("Internal error found in handleVariableInitialization");
            }
            VariableDeclarationFragment frag = (VariableDeclarationFragment)fragments.get(0);
            handleVariableInitialization(frag);
        }
        else if (node instanceof FieldDeclaration) {
            FieldDeclaration fieldDecl = (FieldDeclaration)node;
            List fragments = fieldDecl.fragments();
            if (fragments.size() != 1) {
                ArcumError.fatalError("Internal error found in handleVariableInitialization");
            }
            VariableDeclarationFragment frag = (VariableDeclarationFragment)fragments.get(0);
            handleVariableInitialization(frag);
        }
        else {
            ArcumError.fatalError("Unhandled case: %s", ASTUtil.getDebugString(node));
        }
    }

    private void insertSuperclassRelation(OptionMatchTable table,
        Collection<ASTNode> classes)
    {
        final TraitSignature type;
        final List<String> names;

        type = BUILT_IN_TRAIT_TYPES.get("superclassOf");
        names = Lists.transform(type.getFormals(), FormalParameter.getIdentifier);

        table.addBuiltInTrait(type);
        for (ASTNode clazz : classes) {
            ITypeBinding nextParent = lookupTypeBinding((AbstractTypeDeclaration)clazz);
            for (;;) {
                ITypeBinding superclass = nextParent.getSuperclass();
                if (superclass == null)
                    break;
                Map<String, Object> values = Maps.newHashMap();
                values.put(names.get(0), superclass);
                values.put(names.get(1), clazz);
                EntityTuple instance = new EntityTuple(type, values, null);
                table.addTraitInstance("superclassOf", instance);
                nextParent = superclass;
            }
        }
    }

    private ITypeBinding findDefiningType(@Union("Entity")
    Object entity)
    {
        ASTNode node;
//        if (entity instanceof SignatureEntity) {
//            node = ((SignatureEntity)entity).getSignatureNode();
//        }
//        else {
        node = (ASTNode)entity;
//        }
        ASTNode parent = node.getParent();
        if (parent == null) {
            parent = desugaredToNearestNode.get(node);
        }
        while (!(parent instanceof AbstractTypeDeclaration)) {
            parent = parent.getParent();
        }
        ITypeBinding typeBinding = lookupTypeBinding((AbstractTypeDeclaration)parent);
        return typeBinding;
    }

    public static ASTNode findASTNode(IBinding binding) {
        EntityDataBase edb = currentEDB.peek();
        String key = binding.getKey();
        MethodDeclaration methodDecl = edb.methodBindingKeyLookup.get(key);
        if (methodDecl != null) {
            return methodDecl;
        }
        AbstractTypeDeclaration typeDecl = edb.typeDefinitionKeyLookup.get(key);
        if (typeDecl != null) {
            return typeDecl;
        }
        return null;
    }

    public static IBinding resolveBindingNullOK(Type node) {
        ITypeBinding result = node.resolveBinding();
        return result;
    }

    public static IBinding resolveBindingNullOK(Name node) {
        IBinding result = node.resolveBinding();
        return result;
    }

    public static IBinding resolveBindingNullOK(SingleVariableDeclaration node) {
        IVariableBinding result = node.resolveBinding();
        return result;
    }

    // EXAMPLE: This code duplication could be removed by either using interfaces
    // or by using reflection. All three implementations are useful in different
    // ways.
    public static ITypeBinding resolveBinding(AbstractTypeDeclaration node) {
        return (ITypeBinding)doResolveBinding(node, node.resolveBinding());
    }

    public static IBinding resolveBinding(Name node) {
        return doResolveBinding(node, node.resolveBinding());
    }

    public static IBinding resolveBinding(Type node) {
        return doResolveBinding(node, node.resolveBinding());
    }

    public static IBinding resolveBinding(VariableDeclaration node) {
        return doResolveBinding(node, node.resolveBinding());
    }

    private static IBinding doResolveBinding(final ASTNode toResolve, IBinding binding) {
        if (binding != null) {
            return binding;
        }
        else {
            ASTNode root = toResolve.getRoot();
            EntityDataBase edb = currentEDB.peek();
            ASTNode source = edb.desugaredToNearestNode.get(root);
            final StructuralPropertyDescriptor spdInParent = toResolve
                .getLocationInParent();
            final ASTNode[] result = new ASTNode[1];
            edb.traverseTable.traverseAST(source, new ASTVisitorAdaptor() {
                @Override
                public boolean visitASTNode(ASTNode sourceNode,
                    StructuralPropertyDescriptor edge)
                {
                    if (sourceNode == null) {
                        return false;
                    }
                    if (spdInParent == edge) {
                        if (Entity.compareTo(sourceNode, toResolve) == 0) {
                            result[0] = sourceNode;
                            return false;
                        }
                    }
                    return true;
                }
            });
            ASTNode foundNode = result[0];
            if (foundNode == null) {
                StringBuilder builder = new StringBuilder();
                builder.append(String.format("%n"));
                builder.append(String.format("      From: %s%n", //->
                    StringUtil.minimizeWhitespace(root)));
                builder.append(String.format("To resolve: %s%n", toResolve));
                builder.append(String.format("Looking in: %s%n", //->
                    StringUtil.minimizeWhitespace(source)));
                builder.append(String.format("     found: %s%n", foundNode));
                ArcumError.fatalError("Internal error in doResolveBinding: %s",
                    builder.toString());
                return null;
            }
            else {
                try {
                    Method method = foundNode.getClass().getMethod("resolveBinding");
                    Object methodResult = method.invoke(foundNode);
                    return (IBinding)methodResult;
                }
                catch (RuntimeException e) {
                    throw e;
                }
                catch (Exception e) {
                    ArcumError.fatalError("Something went wrong: %s", e.getMessage());
                    return null;
                }
            }
        }
    }

    public static EntityType getMostSpecificEntityType(Object entity) {
        if (entity instanceof BindingKeyValue) {
            BindingKeyValue keyValue = (BindingKeyValue)entity;
            return keyValue.getType();
        }
        else if (entity instanceof EntityList) {
            // MACNEIL: This is a bug because it could be more specifically an
            // access specifier. This will just be ignored for now.
            return EntityType.MODIFIERS;
        }
        else if (entity instanceof SignatureEntity) {
            return EntityType.SIGNATURE;
        }
        Class<?> clazz = entity.getClass();
        EntityType result = entityTypeTable.get(clazz);
        if (result == null) {
            ArcumError.fatalError("Unhandled case: %s", ASTUtil.getDebugString(entity));
        }
        return result;
    }

    public static boolean isBuiltInTrait(String traitName) {
        return BUILT_IN_TRAIT_NAMES.contains(traitName);
    }
}