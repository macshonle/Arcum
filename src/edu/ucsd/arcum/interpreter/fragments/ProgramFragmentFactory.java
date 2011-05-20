package edu.ucsd.arcum.interpreter.fragments;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;
import static edu.ucsd.arcum.exceptions.ArcumError.fatalError;
import static edu.ucsd.arcum.interpreter.fragments.ModifierElement.*;
import static edu.ucsd.arcum.interpreter.fragments.SubtreeList.copyAllAndAdd;
import static edu.ucsd.arcum.interpreter.fragments.SubtreeList.Kind.ORDER_MATTERS;
import static edu.ucsd.arcum.interpreter.fragments.SubtreeList.Kind.UNORDERED;
import static edu.ucsd.arcum.interpreter.fragments.VariableNode.DONT_CARE;
import static edu.ucsd.arcum.interpreter.parser.BacktrackingScanner.TokenNameARCUMBEGINQUOTE;
import static edu.ucsd.arcum.interpreter.parser.BacktrackingScanner.TokenNameARCUMVARIABLE;
import static java.util.Collections.addAll;
import static java.util.Collections.singletonList;
import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.*;

import java.util.*;

import org.eclipse.jdt.core.dom.*;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.JavaFragmentCompilationProblem;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.exceptions.UserCompilationProblem;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.expressions.PatternExpression;
import edu.ucsd.arcum.interpreter.parser.BacktrackingScanner;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.parser.ArcumStructureParser.EmbeddedExpression;
import edu.ucsd.arcum.interpreter.parser.FragmentParser.TypeBindingWithModifiers;
import edu.ucsd.arcum.interpreter.query.*;
import edu.ucsd.arcum.interpreter.satisfier.TypeLookupTable;
import edu.ucsd.arcum.util.Helper;
import edu.ucsd.arcum.util.MethodGroup;
import edu.ucsd.arcum.util.StringUtil;

@SuppressWarnings("restriction")
public class ProgramFragmentFactory
{
    private static final String VARIABLE_PREFIX = "ARCUM_VARIABLE_";
    private static final String TRAIT_LIST_PREFIX = "ARCUM_TRAIT_LIST_PREFIX_";
    private static final String VARIABLE_ANY = String.format("%sANY", VARIABLE_PREFIX);

    public static final String EMBEDDED_VALUE_PREFIX = "EMBEDDED_VALUE_PREFIX_";

    private final PatternExpression pattern;
    private final IEntityLookup lookup;
    private final List<ProgramFragment> fragments;
    private final TypeLookupTable types;
    private final boolean isMatchingMode;

    public ProgramFragmentFactory(PatternExpression pattern, EntityType type,
        IEntityLookup lookup, TypeLookupTable types, boolean isMatchingMode)
    {
        this.pattern = pattern;
        this.lookup = lookup;
        this.fragments = Lists.newArrayList();
        this.types = types;
        this.isMatchingMode = isMatchingMode;

        String text = pattern.getPattern();
        BacktrackingScanner scanner = new BacktrackingScanner(text);

        SourceLocation position = pattern.getPosition();

        try {
            switch (type) {
            case EXPR:
                fragments.addAll(initialzeExpr(scanner));
                break;
            case FIELD:
                fragments.addAll(initializeFieldDeclaration(scanner));
                break;
            case SIGNATURE:
                fragments.addAll(initializeSignature(scanner));
                break;
            case ACCESS_SPECIFIER:
                fragments.addAll(initializeAccessSpecifier(scanner));
                break;
            case TYPE:
                fragments.addAll(initializeType(scanner, type));
                break;
            case DECLARATION_ELEMENT:
                fragments.addAll(initializeDeclarationElement(scanner));
                break;
            case METHOD:
                fragments.addAll(initializeMethod(scanner));
                break;
            case STATEMENT:
                fragments.addAll(initializeStatement(scanner));
                break;
            case ANNOTATION:
                fragments.addAll(initializeAnntotation(scanner));
                break;
            case ANY:
                ArcumError.fatalUserError(position, "Not enough information to parse");
                break;
            case ERROR:
                ArcumError.fatalUserError(position,
                    "Pattern must be bound in some context to a variable with a type");
                break;
            default:
                ArcumError.fatalUserError(position,
                    "Matching %s's is not supported yet%n", type);
                break;
            }
        }
        catch (JavaFragmentCompilationProblem jfcp) {
            String message = StringUtil.enumerate(jfcp.getMessages());
            ArcumError.fatalUserError(position, "%s", message);
        }

        if (DEBUG) {
            System.out.printf("Turned %s%nInto: %s%n", text, StringUtil.separate(
                fragments, "%n"));
        }
    }

    public List<ProgramFragment> getAbstractProgramFragments() {
        if (DEBUG) {
            System.out.printf("%nReturning fragments:%n");
            for (ProgramFragment fragment : fragments) {
                System.out.printf("%s%n", fragment);
            }
            System.out.printf("%n");
        }
        return fragments;
    }

    // Assuming that an arcum variable is the scanner's current token this
    // will read it in from the scanner and look it up in the entity lookup.
    // If it has already been found, it will return the resolved value for it.
    // Otherwise, it will return a proper branch (either making it a variable
    // node or a previously made node if a pattern exists for it)
    private ProgramFragment matchAndLookupVariable(BacktrackingScanner scanner,
        EntityType type)
    {
        String id = scanner.getCurrentTokenString();
        scanner.match();
        return resolveNodeOrCreateVariable(type, id);
    }

    private ProgramFragment resolveNodeOrCreateVariable(EntityType type, String id) {
        Object entity = lookup.lookupEntity(id);
        if (entity != null) {
            return ResolvedEntity.newInstance(entity);
        }
        else {
            return new VariableNode(type, id);
        }
    }

    private List<ProgramFragment> initializeStatement(BacktrackingScanner scanner)
        throws JavaFragmentCompilationProblem
    {
        String stmtString = fillInArcumVariables(scanner, new HashSet<String>());
        FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
        Statement stmt = fragmentParser.getStatement(stmtString);
        ProgramFragment result = buildSubtreeNodeFromAST(stmt, true, EntityType.STATEMENT);
        return Collections.singletonList(result);
    }

    private List<ProgramFragment> initializeAnntotation(BacktrackingScanner scanner)
        throws JavaFragmentCompilationProblem
    {
        String anntString = fillInArcumVariables(scanner, new HashSet<String>());
        FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
        Annotation annt = fragmentParser.getAnnotation(anntString, pattern.getPosition());
        ProgramFragment result = buildSubtreeNodeFromAST(annt, true,
            EntityType.ANNOTATION);
        return Collections.singletonList(result);
    }

    // e.g.:
    // Field access expressions:
    //  getExpr == [`targetExpr.`field]
    //  setExpr == [`targetExpr.`field = `valExpr]
    //
    // Static field access expressions:
    //  fieldSet == [`targetType.`mapField = `_] (a static field access)
    //  getExpr == [`targetType.`mapField.get(`targetExpr)]
    //  setExpr == [`targetType.`mapField.put(`targetExpr, `valExpr)]
    //
    // Special call patterns:
    //  ([`e.put(`_, `_)] || [`e.get(`_)])
    //
    // Arbitrary expressions:
    //  mapInit == [new WeakIdentityHashMap<`targetType, `attrType>()]
    private List<ProgramFragment> initialzeExpr(BacktrackingScanner scanner)
        throws JavaFragmentCompilationProblem
    {
        // FIXME: finish gathering type information, and reverse the order in which
        // pattern sub-clauses get created, so that more type information is known
        Set<String> locals = new TreeSet<String>();
        String exprString = fillInArcumVariables(scanner, locals);
        FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
        Expression expr = fragmentParser.getExpression(exprString, locals);
        ProgramFragment result = buildSubtreeNodeFromAST(expr, true, EntityType.EXPR);
        return Collections.singletonList(result);
    }

// MACNEIL: Is this just unused, or is there something useful in here worth keeping?
//    private List<ProgramFragment> parseArgumentList(BacktrackingScanner scanner)
//        throws JavaFragmentCompilationProblem
//    {
//        List<ProgramFragment> args = new ArrayList<ProgramFragment>();
//        scanner.match(TokenNameLPAREN);
//        while (scanner.lookaheadIsNot(TokenNameRPAREN, TokenNameEOF)) {
//            List<ProgramFragment> exprs = parseExpression(scanner, true);
//            if (exprs.size() != 1) {
//                ArcumError.fatalError("Multi-patterns currently not"
//                    + " supported-- parseArgumentList");
//            }
//            args.add(exprs.get(0));
//            if (scanner.lookahead() == TokenNameCOMMA)
//                scanner.match(TokenNameCOMMA);
//            else
//                break;
//        }
//        scanner.match(TokenNameRPAREN);
//        return args;
//    }

    // e.g.:
    //   [null]
    //   [5 + i]
    //   [new `MyType()]
    //   [`expr]
    // Where any instances of variables that are Types, Interfaces, or Classes
    // are replaced with their fully-qualified names and then parsed
    private List<ProgramFragment> parseExpression(BacktrackingScanner scanner,
        boolean typesExpected) throws JavaFragmentCompilationProblem
    {
        HashSet<String> locals = new HashSet<String>();
        String exprString = fillInArcumVariables(scanner, locals);
        FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
        Expression expr = fragmentParser.getExpression(exprString, locals);
        ProgramFragment result = buildSubtreeNodeFromAST(expr, typesExpected,
            EntityType.EXPR);
        return singletonList(result);
    }

    private ProgramFragment buildSubtreeNodeFromAST(ASTNode node, boolean typesExpected,
        EntityType entityType)
    {
        ProgramFragment result;

//        if (node instanceof Assignment) {
//            Assignment assignment = (Assignment)node;
//            Expression lhs = assignment.getLeftHandSide();
//        }
        if (node instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess)node;
            Expression target = fieldAccess.getExpression();
            SimpleName fieldName = fieldAccess.getName();
            ProgramFragment targetFragment = buildSubtreeNodeFromAST(target,
                typesExpected, entityType);
            ProgramFragment fieldFragment = lookupFieldName(fieldName);
            result = makeFieldAccessNode(targetFragment, fieldFragment);
        }
        else if (node instanceof QualifiedName) {
            QualifiedName name = (QualifiedName)node;

            Name qualifier = name.getQualifier();
            IBinding qualifierBinding = EntityDataBase.resolveBindingNullOK(qualifier);
            if (qualifierBinding instanceof ITypeBinding) {
                // then it's a class name with a field
                ITypeBinding typeBinding = (ITypeBinding)qualifierBinding;
                ProgramFragment targetExpr = new ResolvedType(typeBinding);
                ProgramFragment field = lookupFieldName(name.getName());
                result = makeFieldAccessNode(targetExpr, field);
            }
            else {
                IBinding binding = EntityDataBase.resolveBindingNullOK(name);
                if (binding != null && binding instanceof ITypeBinding) {
                    // then it's a fully qualified class name without a field
                    ITypeBinding tb = (ITypeBinding)binding;
                    result = new ResolvedType(tb);
                }
                else {
                    // then we assume it's s field reference of another form
                    ProgramFragment targetExpr = buildSubtreeNodeFromAST(qualifier,
                        typesExpected, entityType);
                    ProgramFragment field = lookupFieldName(name.getName());
                    result = makeFieldAccessNode(targetExpr, field);
                }
            }
        }
        else if (node instanceof SimpleName) {
            SimpleName name = (SimpleName)node;
            String lexeme = name.getIdentifier();
            if (lexeme.equals(VARIABLE_ANY)) {
                result = new VariableNode(entityType, DONT_CARE);
            }
            else if (lexeme.startsWith(VARIABLE_PREFIX)) {
                String arcumVarName = lexeme.substring(VARIABLE_PREFIX.length());
                // MACNEIL: maybe lookup its type, had a bug when just a variable
                // was returned, but calling resolveNodeOrCreateVariable fixes this
                result = resolveNodeOrCreateVariable(entityType, arcumVarName);
            }
            else {
                IBinding binding = EntityDataBase.resolveBindingNullOK(name);
                // URGENT (!!!): We need to fill in information when we can,
                // until then, some checks will not be made
                if (binding == null && false && typesExpected) {
                    throw new UserCompilationProblem(String.format(
                        "Cannot resolve \"%s\": Possible mispelling"
                            + " or an import is missing", name.getFullyQualifiedName()));
                }
                if (binding instanceof ITypeBinding) {
                    ITypeBinding tb = (ITypeBinding)binding;
                    // Then it's the name of a class without a field access,
                    // e.g. as in 'new MyClass()'
                    result = new ResolvedType(tb);
                }
                else {
                    // Then it's some other name, e.g. a method name (and may
                    // not technically be an expression)
                    AST ast = node.getAST();
                    SimpleName unparentedSimpleName = ast.newSimpleName(lexeme);
                    result = new ResolvedEntity(unparentedSimpleName);
                }
            }
        }
        else if (isNodeVariable(node)) {
            String arcumVarName = extractNodeVariable(node);
            result = resolveNodeOrCreateVariable(entityType, arcumVarName);
        }
        else {
            PartialNode partial = new PartialNode(node.getClass());
            StructuralPropertyDescriptor[] spds = ASTTraverseTable.getProperties(node);
            edges: for (StructuralPropertyDescriptor spd : spds) {
                ProgramFragment branch;
                Object property = node.getStructuralProperty(spd);
                if (property == null) {
                    continue edges;
                }
                if (spd.isChildProperty()) {
                    ASTNode child = (ASTNode)property;
                    branch = buildSubtreeNodeFromAST(child, typesExpected, entityType);
                }
                else if (spd.isChildListProperty()) {
                    List children = (List)property;
                    if (children.size() == 0
                        && canBeCanonicalizedAway((ChildListPropertyDescriptor)spd))
                    {
                        continue edges;
                    }
                    if (isListVariable(children)) {
                        String arcumVarName = extractListVariable(children);
                        branch = (ProgramFragment)lookup.lookupEntity(arcumVarName);
                    }
                    else {
                        List<ProgramFragment> nodes = new ArrayList<ProgramFragment>();
                        for (Object child : children) {
                            ProgramFragment element;
                            element = buildSubtreeNodeFromAST((ASTNode)child,
                                typesExpected, entityType);
                            nodes.add(element);
                        }
                        branch = new SubtreeList(nodes, ORDER_MATTERS);
                    }
                }
                else /* if (spd.isSimpleProperty()) */{
                    Object value = property;
                    branch = new SimplePropertyLeaf(value);
                }
                partial.addBranch(spd, branch);
            }
            result = reclassifyPartialNode(partial);
        }
        return result;
    }

    private boolean isNodeVariable(ASTNode node) {
        String arcumVariable = extractNodeVariable(node);
        return arcumVariable != null;
    }

    private String extractNodeVariable(ASTNode node) {
        String arcumVariable = null;
        String name = null;
        if (node instanceof SimpleName) {
            SimpleName simpleName = (SimpleName)node;
            name = simpleName.getIdentifier();
        }
        else if (node instanceof MethodInvocation) {
            MethodInvocation methodInvocation = (MethodInvocation)node;
            name = methodInvocation.getName().getIdentifier();
        }
        if (name != null) {
            if (name.startsWith(TRAIT_LIST_PREFIX)) {
                String indexStr = name.substring(TRAIT_LIST_PREFIX.length());
                arcumVariable = String.format("%s%s", EMBEDDED_VALUE_PREFIX, indexStr);
            }
        }
        return arcumVariable;
    }

    private boolean isListVariable(List children) {
        String arcumVariable = extractListVariable(children);
        return arcumVariable != null;
    }

    private String extractListVariable(List children) {
        if (children.size() == 1) {
            String arcumVariable = null;
            Object object = children.get(0);
            Expression expr = null;
            if (object instanceof ExpressionStatement) {
                ExpressionStatement exprStmt = (ExpressionStatement)object;
                expr = exprStmt.getExpression();
            }
            else if (object instanceof Expression) {
                expr = (Expression)object;
            }
            if (expr != null) {
                arcumVariable = extractNodeVariable(expr);
            }
            return arcumVariable;
        }
        else {
            return null;
        }
    }

    // Is the given edge something where, if the user input fragment is empty, then
    // we should assume we shouldn't even inspect the edge? For example, we want to
    // match a given class, even if it has annotations on it. It would only be when
    // the annotations were present that they would have to match against the
    // remaining list.
    //
    // This whole procedure can be thought of as a short-hand. For example,
    //   [public void foo()]
    // is really short-hand for
    //   [`{Annotation... _} public void foo()]
    // where the backtick'ed expression matches zero or more annotations and puts
    // then in a dummy variable. The "modifiers"-like access to annotations in the
    // JDT DOM can make this a little more complex to actually code, but the
    // access-specifier hack is similar and perhaps it could be abstracted away.
    // That is, the modifiers list really should be three different lists:
    // (1) the list of annotations; (2) the singleton-list of the access specifier;
    // and (3) the list of modifiers.
    //
    // MACNEIL: Put the above comment somewhere in the Arcum Users Manual.
    private boolean canBeCanonicalizedAway(ChildListPropertyDescriptor edge) {
        // MACNEIL: Look at above comment and make this method actually do something
        // in this annotation case, but that will have to wait until list support.
        if (edge == MethodInvocation.TYPE_ARGUMENTS_PROPERTY) {
            return true;
        }
        else {
            return false;
        }
    }

    private ProgramFragment lookupFieldName(SimpleName name) {
        String lexeme = name.toString();
        ProgramFragment result;
        if (lexeme.equals(VARIABLE_ANY)) {
            result = new VariableNode(EntityType.EXPR, DONT_CARE);
        }
        else if (lexeme.startsWith(VARIABLE_PREFIX)) {
            String arcumVarName = lexeme.substring(VARIABLE_PREFIX.length());
            // MACNEIL: maybe lookup its type, had a bug when just a variable
            // was returned, but calling resolveNodeOrCreateVariable fixes this
            result = resolveNodeOrCreateVariable(EntityType.EXPR, arcumVarName);
        }
        else {
            result = new ResolvedEntity(name/*$$$$$$$$$lexeme*/);
        }
        return result;
    }

    // A more specific ProgramFragment type might exist, meaning the general
    // PartialNode data should become specialized
    private ProgramFragment reclassifyPartialNode(PartialNode partial) {
        ProgramFragment result = partial;
        if (partial.getRootType().equals(Assignment.class)) {
            ProgramFragment lhs = partial.lookupEdge(Assignment.LEFT_HAND_SIDE_PROPERTY);
            ProgramFragment rhs = partial.lookupEdge(Assignment.RIGHT_HAND_SIDE_PROPERTY);
//            // TODO: we should grab which kind of assignment it is too. For now,
//            // we assume simple (i.e. not compound) assignment
            if (lhs instanceof FieldAccessPattern) {
                FieldAccessPattern fieldAccess = (FieldAccessPattern)lhs;
                result = new FieldAssignmentPattern(fieldAccess, rhs);
            }
        }
        return result;
    }

    // getExpr == [`targetType.`mapField.get(`targetExpr)]
    // URGENT: field accesses embedded in the argument list may not currently be
    // supported (this has not been tested)
    private List<ProgramFragment> parseStaticFieldAccessMethodCall(
        ProgramFragment targetExpr, ProgramFragment field, BacktrackingScanner scanner)
        throws JavaFragmentCompilationProblem
    {
        Object fieldEntity = ((ResolvedEntity)field).getValue();
        FieldDeclaration fieldASTNode = (FieldDeclaration)Entity
            .getASTNodeValue(fieldEntity);
        List<?> fragments = fieldASTNode.fragments();
        if (fragments.size() != 1) {
            ArcumError.fatalError("Cannot support multiple variable declarations yet.%n");
        }
        VariableDeclarationFragment varFrag = (VariableDeclarationFragment)fragments
            .get(0);
        SimpleName fieldVarName = varFrag.getName();
        ResolvedEntity fieldVarNameEntity = new ResolvedEntity(fieldVarName);

        // target expression
        FieldAccessPattern expression = makeFieldAccessNode(targetExpr, field);

        // method call expression
        // URGENT: need to do this trick in some other method call contexts too
        List<ProgramFragment> results = parseExpression(scanner, false);
        check: for (ProgramFragment programFragment : results) {
            if (programFragment instanceof PartialNode) {
                PartialNode methodCall = (PartialNode)programFragment;
                Class rootType = methodCall.getRootType();
                if (MethodInvocation.class.isAssignableFrom(rootType)) {
                    methodCall
                        .addBranch(MethodInvocation.EXPRESSION_PROPERTY, expression);
                    continue check;
                }
            }
            ArcumError.fatalError("Expected a method call: %s", programFragment);
        }
        return results;
    }

    private FieldAccessPattern makeFieldAccessNode(ProgramFragment targetExpr,
        ProgramFragment field)
    {
        FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
        FieldAccessPattern fieldAccess = new FieldAccessPattern(targetExpr, field,
            fragmentParser);
        return fieldAccess;
    }

    // entityType is assumed to be TYPE, CLASS, or INTERFACE
	private List<ProgramFragment> initializeType(BacktrackingScanner scanner,
        EntityType entityType) throws JavaFragmentCompilationProblem
    {
        String typeString = fillInArcumVariables(scanner, new HashSet<String>());
        FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
        // MACNEIL : This is a bit of a hack We need some other way to determine
        // if it's a type declaration versus a type reference. Perhaps by seeing
        // if the keywords appear *before* any punctuation like ( and {.
        ProgramFragment result;
        BacktrackingScanner checker = new BacktrackingScanner(typeString);
        if (checker.containsToken(TokenNameinterface, TokenNameclass, TokenNameenum)) {
            // Then it's a type declaration
            // MACNEIL: What was this doing?
//            if (typeString.contains(SET_VARIABLE_PREFIX)) {
//                typeString = insertSetPlaceholder(typeString, entityType);
//            }

            AbstractTypeDeclaration decl = fragmentParser.getTypeDeclaration(typeString);
            result = buildSubtreeNodeFromAST(decl, false, entityType);
        }
        else {
            // Then it's a type reference
            TypeBindingWithModifiers tbwm = fragmentParser.getTypeBindingWithModifiers(typeString);
            result = new ResolvedType(tbwm.typeBinding);
        }
        return Collections.singletonList(result);
    }

    private List<ProgramFragment> initializeDeclarationElement(BacktrackingScanner scanner)
        throws JavaFragmentCompilationProblem
    {
        String typeString = fillInArcumVariables(scanner, new HashSet<String>());
        FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
        TypeBindingWithModifiers tbwm = fragmentParser.getTypeBindingWithModifiers(typeString);
        ProgramFragment result = new DeclarationElement(tbwm.typeBinding, tbwm.modifiers);
        return Collections.singletonList(result);
    }

    // MACNEIL: What was this doing?
//    private String insertSetPlaceholder(String pseudoSrc, EntityType entityType) {
//        if (EntityType.TYPE.isAssignableFrom(entityType)) {
//            while (pseudoSrc.contains(SET_VARIABLE_PREFIX)) {
//                int start = pseudoSrc.indexOf(SET_VARIABLE_PREFIX);
//                int i = start;
//                StringBuilder idBuilder = new StringBuilder();
//                for (;;) {
//                    char t = pseudoSrc.charAt(i);
//                    if (!Character.isJavaIdentifierPart(t))
//                        break;
//                    idBuilder.append(t);
//                }
//                String id = idBuilder.toString();
//            }
//            return pseudoSrc;
//        }
//        else {
//            ArcumError.fatalError("Can't handle this situation");
//            return null;
//        }
//    }

    private List<ProgramFragment> initializeMethod(BacktrackingScanner scanner)
        throws JavaFragmentCompilationProblem
    {
        String text = pattern.getPattern();
        if (!text.contains("{") && !text.contains(";")) {
            return initializeSignature(scanner);
        }
        else {
            String methodStr = fillInArcumVariables(scanner, new HashSet<String>());
            FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
            MethodDeclaration methodDecl = fragmentParser.getMethod(methodStr);
            ProgramFragment result = buildSubtreeNodeFromAST(methodDecl, true,
                EntityType.METHOD);
            return Collections.singletonList(result);
        }
    }

    private List<ProgramFragment> initializeSignature(BacktrackingScanner scanner)
        throws JavaFragmentCompilationProblem
    {
        String signatureStr = String.format("%s {}", fillInArcumVariables(scanner,
            new HashSet<String>()));
        FragmentParser fragmentParser = lookup.newParser(isMatchingMode);
        MethodDeclaration methodDecl = fragmentParser.getMethod(signatureStr);
        SignatureEntity signature = new SignatureEntity(methodDecl);
        ProgramFragment result = new ResolvedEntity(signature);
        return Collections.singletonList(result);
    }

    private String fillInArcumVariables(BacktrackingScanner scanner,
        Set<String> localDecls)
    {
        StringBuilder buff = new StringBuilder();
        int currentQuoted = 0;
        while (scanner.lookahead() != TokenNameEOF) {
            currentQuoted = _insertAndMatch(scanner, buff, currentQuoted, localDecls);
            buff.append(" ");
        }
        return buff.toString();
    }

    @Helper(@MethodGroup(type = ProgramFragmentFactory.class, names = "fillInArcumVariables"))
    private int _insertAndMatch(BacktrackingScanner scanner, StringBuilder buff,
        int currentQuoted, Set<String> localDecls)
    {
        String lexeme = scanner.getCurrentTokenString();
        if (scanner.lookaheadEquals(TokenNameARCUMBEGINQUOTE)) {
            buff.append(TRAIT_LIST_PREFIX);
            buff.append(currentQuoted);
            EmbeddedExpression embed = pattern.getEmbeddedExpression(currentQuoted);
            int numTokens = embed.getNumTokens();
            for (int i = 0; i < numTokens; ++i) {
                // eat the expression, we're just putting a place-holder for it here
                scanner.match();
            }
            FormalParameter boundVar = embed.getBoundVar();
            EntityType type = boundVar.getType();
            if (type == EntityType.STATEMENT) {
                // make it a valid statement
                buff.append("(); ");
            }
            ++currentQuoted;
        }
        else if (scanner.lookaheadEquals(TokenNameARCUMVARIABLE)) {
            @Union("Entity") Object entity = lookup.lookupEntity(lexeme);
            Object realEntity = entity;

            if (entity instanceof SubtreeList
                || (entity instanceof ASTNode && !(entity instanceof FieldDeclaration) && !(entity instanceof TypeDeclaration)))
            {
                // Signal to fill it in later, since it already contains ASTNodes
                // or is an ASTNode we do not need to unparse and reparse them
                entity = null;
            }
            if (entity == null) {
                String nameForTemp;
                if (lexeme.equals(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE)) {
                    nameForTemp = VARIABLE_ANY;
                }
                else {
                    nameForTemp = String.format("%s%s", VARIABLE_PREFIX, lexeme);
                }
                buff.append(nameForTemp);
                if (realEntity instanceof Expression) {
                    Expression expression = (Expression)realEntity;
                    ITypeBinding type = expression.resolveTypeBinding();
                    String qualifiedName;
                    if (type.isNullType()) {
                        qualifiedName = "Object";
                    }
                    else {
                        qualifiedName = type.getQualifiedName();
                    }
                    localDecls.add(String.format("%s %s", qualifiedName, nameForTemp));
                }
            }
            else if (entity instanceof ITypeBinding) {
                ITypeBinding typeBinding = (ITypeBinding)entity;
                String qualifiedName = typeBinding.getQualifiedName();
                buff.append(qualifiedName);
            }
            else if (entity instanceof TypeDeclaration) {
                TypeDeclaration typeDeclaration = (TypeDeclaration)entity;
                String qualifiedName = typeDeclaration.resolveBinding()
                    .getQualifiedName();
                buff.append(qualifiedName);
            }
            else if (entity instanceof FieldDeclaration) {
                FieldDeclaration fieldDecl = (FieldDeclaration)entity;
                Type type = fieldDecl.getType();
                VariableDeclarationFragment var = ((VariableDeclarationFragment)fieldDecl
                    .fragments().get(0));
                String id = var.getName().getIdentifier();
                buff.append(id);
            }
            else if (entity instanceof String) {
                String string = (String)entity;
                buff.append(string);
            }
            else {
                fatalError("Expected a resolved type here or an expression.");
            }
        }
        else {
            buff.append(lexeme);
        }
        scanner.match();
        buff.append(" ");
        return currentQuoted;
    }

    // e.g.: [?transient `spec `attrType `attrName]
    private List<ProgramFragment> initializeFieldDeclaration(BacktrackingScanner scanner)
    {
        List<ProgramFragment> result = new ArrayList<ProgramFragment>();

        List<? extends ProgramFragment> modifierLists = initializeModifiersList(scanner);
        ProgramFragment type = parseType(scanner);
        ProgramFragment name = parseVariableDeclarationFragment(scanner);

        for (ProgramFragment modifierList : modifierLists) {
            PartialNode fieldDecl = new PartialNode(FieldDeclaration.class);
            SubtreeList fragmentsList;
            fragmentsList = new SubtreeList(singletonList(name), ORDER_MATTERS);
            fieldDecl.addBranch(FieldDeclaration.FRAGMENTS_PROPERTY, fragmentsList);
            fieldDecl.addBranch(FieldDeclaration.TYPE_PROPERTY, type);
            fieldDecl.addBranch(FieldDeclaration.MODIFIERS2_PROPERTY, modifierList);
            result.add(fieldDecl);
        }

        return result;
    }

    private List<ProgramFragment> initializeAccessSpecifier(BacktrackingScanner scanner) {
        ArcumError.fatalError("Not supporting access specifiers just yet");
        return null;
    }

    private ProgramFragment parseType(BacktrackingScanner scanner) {
        if (currentVariableTypeMatches(scanner, EntityType.TYPE)) {
            return matchAndLookupVariable(scanner, EntityType.TYPE);
        }
        else {
            ArcumError.fatalError("Not supporting other Type parsing just yet");
            return null;
        }
    }

    private ProgramFragment parseVariableDeclarationFragment(BacktrackingScanner scanner)
    {
        PartialNode varDeclFrag = new PartialNode(VariableDeclarationFragment.class);
        PartialNode nameBranch = new PartialNode(SimpleName.class);
        ProgramFragment identifier;
        if (scanner.lookaheadEquals(TokenNameIdentifier)) {
            String name = scanner.getCurrentTokenString();
            scanner.match();
            identifier = new SimplePropertyLeaf(name);
        }
        else if (currentVariableTypeMatches(scanner, EntityType.STRING)) {
            identifier = matchAndLookupVariable(scanner, EntityType.STRING);
        }
        else {
            ArcumError.fatalError("Not supporting other Variable parsing just yet");
            return null;
        }
        nameBranch.addBranch(SimpleName.IDENTIFIER_PROPERTY, identifier);
        varDeclFrag.addBranch(VariableDeclarationFragment.NAME_PROPERTY, nameBranch);

        if (scanner.lookahead() == TokenNameEQUAL) {
            ProgramFragment initExpr;

            scanner.match(TokenNameEQUAL);
            initExpr = matchAndLookupVariable(scanner, EntityType.EXPR);
            varDeclFrag.addBranch(VariableDeclarationFragment.INITIALIZER_PROPERTY,
                initExpr);
        }

        return varDeclFrag;
    }

    private List<? extends ProgramFragment> initializeModifiersList(
        BacktrackingScanner scanner)
    {
        List<SubtreeList> result = new ArrayList<SubtreeList>();
        while (firstOfParseModifiers(scanner)) {
            int token = scanner.lookahead();
            String var = scanner.getCurrentTokenString();
            if (token == TokenNameQUESTION) {
                scanner.match();
                int mod = scanner.lookahead();
                if (!ARCUM_MODIFIER_TOKEN.contains(mod)) {
                    ArcumError.fatalError("Invalid modifier: %s%n", var);
                }
                ModifierElement keyword = tokenToModifier(mod);
                scanner.match();
                ResolvedEntity keywordEntity = new ResolvedEntity(keyword);
                List<SubtreeList> list;
                list = copyAllAndAdd(result, keywordEntity, UNORDERED);
                result.addAll(list);
            }
            else if (ARCUM_MODIFIER_TOKEN.contains(token)) {
                scanner.match();
                ModifierElement keyword = tokenToModifier(token);
                ResolvedEntity keywordEntity = new ResolvedEntity(keyword);
                SubtreeList.addToAll(result, keywordEntity, UNORDERED);
            }
            else if (currentVariableTypeMatches(scanner, EntityType.ACCESS_SPECIFIER)) {
                String id = scanner.getCurrentTokenString();
                ProgramFragment node = matchAndLookupVariable(scanner,
                    EntityType.ACCESS_SPECIFIER);
                SubtreeList.addToAll(result, node, UNORDERED);
            }
        }
        if (result.isEmpty()) {
            ResolvedEntity spec = new ResolvedEntity(ModifierElement.MOD_PACKAGE);
            ArrayList<ResolvedEntity> specList = Lists.newArrayList(spec);
            result.add(new SubtreeList(specList, SubtreeList.Kind.UNORDERED));
        }
        return result;
    }

    private boolean firstOfParseModifiers(BacktrackingScanner scanner) {
        int token = scanner.lookahead();
        return token == TokenNameQUESTION || ARCUM_MODIFIER_TOKEN.contains(token)
            || currentVariableTypeMatches(scanner, EntityType.ACCESS_SPECIFIER);
    }

    static ModifierElement tokenToModifier(int mod) {
        switch (mod) {
        case TokenNamestatic:
            return MOD_STATIC;
        case TokenNameabstract:
            return MOD_ABSTRACT;
        case TokenNamefinal:
            return MOD_FINAL;
        case TokenNamenative:
            return MOD_NATIVE;
        case TokenNamesynchronized:
            return MOD_SYNCHRONIZED;
        case TokenNametransient:
            return MOD_TRANSIENT;
        case TokenNamevolatile:
            return MOD_VOLATILE;
        case TokenNamestrictfp:
            return MOD_STRICTFP;
        case TokenNamepublic:
            return MOD_PUBLIC;
        case TokenNameprotected:
            return MOD_PROTECTED;
        case TokenNameprivate:
            return MOD_PRIVATE;
        case TokenNamepackage:
            return MOD_PACKAGE;
        default:
            return null;
        }
    }

    private static Set<Integer> ARCUM_MODIFIER_TOKEN = new TreeSet<Integer>();
    private static Set<Integer> ARCUM_ACCESS_SPECIFIER_TOKEN = new TreeSet<Integer>();
    static {
        addAll(ARCUM_ACCESS_SPECIFIER_TOKEN, TokenNamepublic, TokenNameprotected,
            TokenNameprivate, TokenNamepackage);
        addAll(ARCUM_MODIFIER_TOKEN, TokenNamestatic, TokenNameabstract, TokenNamefinal,
            TokenNamenative, TokenNamesynchronized, TokenNametransient,
            TokenNamevolatile, TokenNamestrictfp);
        ARCUM_MODIFIER_TOKEN.addAll(ARCUM_ACCESS_SPECIFIER_TOKEN);
    }

    private boolean currentVariableTypeMatches(BacktrackingScanner scanner,
        EntityType expectedType)
    {
        if (scanner.lookahead() == TokenNameARCUMVARIABLE) {
            String varID = scanner.getCurrentTokenString();

            if (varID.equals(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE)) {
                // the placeholder "dummy" variable matches all types
                return true;
            }
            else {
                EntityType type = types.lookupType(varID);
                if (type == null) {
                    type = lookup.findResolvedSingleton(varID).getType();
                }
                return expectedType.isAssignableFrom(type);
            }
        }
        else {
            return false;
        }
    }
}