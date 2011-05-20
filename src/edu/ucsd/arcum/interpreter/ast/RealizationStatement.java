package edu.ucsd.arcum.interpreter.ast;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static edu.ucsd.arcum.ArcumPlugin.DEBUG;
import static edu.ucsd.arcum.exceptions.ArcumError.fatalUserError;
import static edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable.SPECIAL_ANY_VARIABLE;
import static edu.ucsd.arcum.interpreter.query.EntityDataBase.CHILD_VAR_REF;
import static edu.ucsd.arcum.interpreter.query.EntityDataBase.PARENT_VAR_REF;
import static edu.ucsd.arcum.util.StringUtil.separate;
import static java.lang.String.format;

import java.util.*;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.expressions.BooleanConjunction;
import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;
import edu.ucsd.arcum.interpreter.query.*;
import edu.ucsd.arcum.interpreter.satisfier.NodesWithLocations;
import edu.ucsd.arcum.interpreter.satisfier.Satisfier;
import edu.ucsd.arcum.util.Graph;
import edu.ucsd.arcum.util.ReadOnly;
import edu.ucsd.arcum.util.StringUtil;
import edu.ucsd.arcum.util.Graph.LayeredVisitor;

public class RealizationStatement implements Constrainable
{
    protected final TopLevelConstruct declaration;
    private SourceLocation position;

    private List<TraitSignature> tuplesRealized;
    private ConstraintExpression expression;
    private List<ConstraintExpression> requireClauses;
    private List<ErrorMessage> messages;
    private ErrorMessage singletonErrorMessage = ErrorMessage.EMPTY_MESSAGE;

    public RealizationStatement(TopLevelConstruct declaration, SourceLocation position) {
        this.declaration = declaration;
        this.position = position;

        this.tuplesRealized = Lists.newArrayList();
        this.expression = null;
        this.requireClauses = Lists.newArrayList();
        this.messages = Lists.newArrayList();

        declaration.addRealizationStatement(this);
    }

    // makes note of the trait being realized here, but the statement is not valid
    // until it has been type checked
    public void addTraitSignature(TraitSignature signature) {
        tuplesRealized.add(signature);
    }

    // checks that either: (1) only singletons are realized; or (2) only
    // non-singletons are realized.
    public void typeCheckAndValidate(OptionInterface optionInterface) {
        TraitSignature intfSignature = null;
        int numSingletons = 0;
        int numTraits = 0;
        for (int i = 0; i < tuplesRealized.size(); ++i) {
            TraitSignature signature = tuplesRealized.get(i);
            String name = signature.getName();
            if (optionInterface.declaresAsAbstract(name)) {
                intfSignature = optionInterface.getTraitSignature(name);
                // check consistency with the interface
                if (!signature.implementsSignature(intfSignature)) {
                    StringBuilder builder = new StringBuilder();
                    intfSignature.enterTraitNames(builder);
                    ArcumError.fatalUserError(position,
                        "This realization must match \'%s\'", builder);
                }
                signature.inheritConstraints(intfSignature);
                // find out if it's singleton or not
                if (intfSignature.isSingleton()) {
                    ++numSingletons;
                }
                else {
                    ++numTraits;
                }
            }
            else {
                // not realizing an abstract trait, so it must be defined locally
                signature.setOptionLocal(true);
            }
        }
        if (numSingletons > 0 && numTraits > 0) {
            ArcumError.fatalUserError(getPosition(),
                "The realization statement %s is invalid: cannot"
                    + " realize singletons with non-singletons", this);
        }
        if (numTraits > 1) {
            ArcumError.fatalUserError(getPosition(), "Invalid realization:"
            		+ " Cannot realize multiple concepts with the same expression");
        }
        if (numTraits == 1) {
            ConstraintExpression toConjoin = intfSignature.getInterfaceConjunct();
            this.expression = BooleanConjunction.conjoin(toConjoin, this.expression);
        }
        
        // TASK: call ArcumDeclarationCode.checkFormals(formals); for each formals list
        // and do the same for the interface too (somewhere else)
        addRequirementsToTraitConstraints();
    }

    protected void addRequirementsToTraitConstraints() {
        if (requireClauses.size() != messages.size()) {
            ArcumError.fatalError("Assert failed: Internal issue with error messages");
        }
        Iterator<ConstraintExpression> i = requireClauses.iterator();
        Iterator<ErrorMessage> j = messages.iterator();
        while (i.hasNext()) {
            ConstraintExpression requirement = i.next();
            ErrorMessage message = j.next();
            for (TraitSignature type : tuplesRealized) {
                type.addRequiresClause(requirement, message);
            }
        }
    }

    public void setBodyExpression(ConstraintExpression bodyExpr) {
        this.expression = bodyExpr;
    }

    public List<ConstraintExpression> getRequireClauses() {
        return requireClauses;
    }

    public List<ErrorMessage> getErrorMessages() {
        return messages;
    }

    public void addRequiresClause(ConstraintExpression clause, ErrorMessage message) {
        requireClauses.add(clause);
        messages.add(message);
        for (TraitSignature signature : tuplesRealized) {
            // We add the require clauses to all signatures because in the tuple
            // case there is only one signature anyway, and in the singleton case
            // the small redundancy of checking the same condition is ok. If this
            // is a problem we can mark clauses that are already checked, and thus
            // skip them. Or, we can just add the clauses to only the first one
            // instead of all of them. MACNEIL: Possible bug if the names aren't in
            // scope during the check. They likely will be, however.
            signature.addRequiresClause(clause, message);
        }
    }

    public void addSingletonErrorMessage(ErrorMessage message) {
        this.singletonErrorMessage = message;
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("realize ");
        buff.append(StringUtil.separate(Lists.transform(tuplesRealized,
            new Function<TraitSignature, String>() {
                @Override public String apply(TraitSignature signature) {
                    return signature.toSignatureOnlyString();
                }
            })));
        buff.append(" with\n    ");
        buff.append(expression.toString());
        buildRequiresMessageString(buff, this);
        return buff.toString();
    }

    public static void buildRequiresMessageString(StringBuilder buff,
        Constrainable constrainable)
    {
        List<ConstraintExpression> conditions = constrainable.getRequireClauses();
        List<ErrorMessage> messages = constrainable.getErrorMessages();
        if (conditions.size() == 0 && messages.size() > 0) {
            checkPairConsistency(conditions, messages);
            ErrorMessage message = messages.get(0);
            appendNonEmptyErrorMessage(buff, message);
        }
        else {
            checkPairConsistency(conditions, messages);
            for (int i = 0; i < conditions.size(); ++i) {
                ConstraintExpression requires = conditions.get(i);
                ErrorMessage errorMessage = messages.get(i);
                buff.append(String.format("%n  require"));
                appendNonEmptyErrorMessage(buff, errorMessage);
                buff.append(" {");
                buff.append(requires.toString());
                buff.append("} ");
            }
        }
    }

    public static void checkPairConsistency(List<ConstraintExpression> conditions,
        List<ErrorMessage> messages)
    {
        if (conditions.size() == 0 && messages.size() > 0) {
            if (messages.size() != 1) {
                ArcumError.fatalError("Internal error: should just have one"
                    + " error message");
            }
        }
        else if (conditions.size() != messages.size()) {
            ArcumError.fatalError("Internal error: should have parity of clauses"
                + " and error messages even when error message is empty");
        }
    }

    public static void appendNonEmptyErrorMessage(StringBuilder buff, ErrorMessage errorMessage)
    {
        if (errorMessage != ErrorMessage.EMPTY_MESSAGE) {
            buff.append(errorMessage.toString());
            buff.append(String.format("%n"));
        }
    }

    public boolean isSingletonRealization() {
        // TASK: probably this has already been typechecked, so we just return the
        // status of the first
        return tuplesRealized.get(0).isSingleton();
    }

    public boolean isLocal() {
        return tuplesRealized.get(0).isOptionLocal();
    }

    private interface SingletonOperation
    {
        void apply(RealizationStatement singletonStmt) throws CoreException;
    }

    private static abstract class LayerAdaptor implements
        Graph.LayeredVisitor<String, CoreException>
    {
        final List<? extends RealizationStatement> stmts;

        public LayerAdaptor(List<? extends RealizationStatement> stmts) {
            this.stmts = stmts;
        }

        @Override public void cycleFound(List<String> cycle) throws CoreException {
            RealizationStatement next = stmts.iterator().next();
            fatalUserError(next.getPosition(), "(Source Position of this"
                + " error message is not accurate) Cycle related to negation, forall,"
                + " or equivalence: %s", StringUtil.separate(cycle));
        }
    }

    public static void collectivelyRealizeStatements(
        final List<? extends RealizationStatement> stmts, final EntityDataBase edb,
        final OptionMatchTable table) throws CoreException
    {
        final @ReadOnly Map<String, RealizationStatement> stmtLookup = makeStatementLookup(stmts);
        final Graph<String> dependencies = makeDependencyGraph(stmts, table, stmtLookup);

        Graph.LayeredVisitor<String, CoreException> realizePerLayer;
        realizePerLayer = new LayerAdaptor(stmts) {
            @Override public void visitLayer(List<String> layer) throws CoreException {
                List<RealizationStatement> stmts = Lists.newArrayList();
                for (String traitName : layer) {
                    // EXAMPLE: Note the subtle difference with containsKey versus
                    // apply(..) != null when it comes to the null key!
                    RealizationStatement stmt = stmtLookup.get(traitName);
                    if (stmt != null && !stmts.contains(stmt)) {
                        stmts.add(stmt);
                    }
                }
                realizeFixedPoint(edb, table, stmts);
            }
        };
        dependencies.iterateOverTopologicalLayers(realizePerLayer);
    }

    private static Graph<String> makeDependencyGraph(
        List<? extends RealizationStatement> stmts, OptionMatchTable table,
        @ReadOnly Map<String, RealizationStatement> stmtLookup)
    {
        Set<String> alreadyRealized = table.getTraitsRealized();
        Graph<String> g = new Graph<String>();
        for (RealizationStatement stmt : stmts) {
            List<TraitSignature> realized = stmt.getTuplesRealized();
            List<String> dependencies = stmt.findEvaluationDependencies(stmtLookup);
            for (TraitSignature tc : realized) {
                if (tc.isSingleton()) {
                    for (String name : tc.getNamesOfDeclaredFormals()) {
                        g.addNode(name);
                        for (String dependency : dependencies) {
                            g.addEdge(dependency, name);
                        }
                    }
                }
                else {
                    String traitName = tc.getName();
                    g.addNode(traitName);
                    for (String dependency : dependencies) {
                        g.addEdge(dependency, traitName);
                    }
                }
            }
        }
        if (DEBUG) {
            System.out.printf("%n<graph>%n%s</graph>%n", g);
        }
        return g;
    }

    // 1. All traits non-monotonically depended upon must be realized first
    // 2. All traits that a singleton references must be realized first
    // 3. All singletons referenced must be realized first
    // 4. Exclude what is already realized, what is already predefined, and what is
    //    realized by this statement
    // 5. Check that the any variable is not being misused
    // EXAMPLE: A "read only set" is like a Predicate
    private List<String> findEvaluationDependencies(
        @ReadOnly Map<String, RealizationStatement> stmtLookup)
    {
        final Set<String> realizedByMe = getVariablesRealized();
        final Set<String> predefined = getPredefinedTraitNames();
        {
            Set<String> builtIns = EntityDataBase.getBuiltInTraitAndPredicateNames();
            predefined.addAll(builtIns);
        }

        Set<String> traitDependencies = expression.findAllTraitDependencies();
        // (5)
        if (traitDependencies.contains(SPECIAL_ANY_VARIABLE)
            && excludesUseOfAnyVariable())
        {
            ArcumError.fatalUserError(expression.getPosition(), "Cannot refer to"
                + " special \"%s\" variable in this context", SPECIAL_ANY_VARIABLE);
        }
        traitDependencies.removeAll(predefined);
        traitDependencies.removeAll(realizedByMe);
        traitDependencies.remove(SPECIAL_ANY_VARIABLE);

        Set<String> result = Sets.newHashSet();
        if (this.isSingletonRealization() || this.isLocal()) {
            // (2)
            result.addAll(traitDependencies);
        }
        else {
            // (3)
            // just add the singletons and locals
            for (String traitDependency : traitDependencies) {
                // TASK: predefines should add trait param singleton-like dudes
                if (predefined.contains(traitDependency)) {
                    continue;
                }
                // TASK !!!!!!: stmtLookup should work with singleton dudes too //// !!!!
                RealizationStatement dependency = stmtLookup.get(traitDependency);
                if (dependency.isSingletonRealization() || dependency.isLocal()) {
                    result.add(traitDependency);
                }
            }
        }

        // (1)
        Set<String> nonMonotonicDependencies = expression.findNonMonotonicDependencies();
        result.addAll(nonMonotonicDependencies);

        // (4)
        result.removeAll(predefined);
        result.removeAll(realizedByMe);
        result.remove(SPECIAL_ANY_VARIABLE);

        return Lists.newArrayList(result);
    }

    protected boolean excludesUseOfAnyVariable() {
        return true;
    }

    // MACNEIL -- For these sets we should probably support overloading by appending
    // a special character and then the number of parameters. Then, uses will be
    // looked up based on the number of arguments. Until then, there is no name
    // overloading, so we just use the names raw.
    private Set<String> getPredefinedTraitNames() {
        Set<String> result = Sets.newHashSet();
        List<TraitSignature> tcsToAdd;
        if (declaration instanceof OptionInterface) {
            OptionInterface optionInterface = (OptionInterface)declaration;
            tcsToAdd = optionInterface.getSubTraitConstraints();
        }
        else if (declaration instanceof Option) {
            Option option = (Option)declaration;
            OptionInterface optionInterface = option.getOptionInterface();
            tcsToAdd = optionInterface.getTraitSignatures();
        }
        else {
            tcsToAdd = Collections.emptyList();
        }
        for (TraitSignature tc : tcsToAdd) {
            if (tc.isSingleton()) {
                for (String name : tc.getNamesOfDeclaredFormals()) {
                    result.add(name);
                }
            }
            else {
                result.add(tc.getName());
            }
        }
        return result;
    }

    private Set<String> getVariablesRealized() {
        Set<String> result = Sets.newHashSet();
        for (TraitSignature tupleRealized : tuplesRealized) {
            List<FormalParameter> formals = tupleRealized.getFormals();
            List<String> vars = transform(formals, FormalParameter.getIdentifier);
            result.addAll(vars);
        }
        return result;
    }

    // EXAMPLE: Could return @ReadOnly Map<String, RealizationStatement> or
    // Function<String, RealizationStatement>
    private static @ReadOnly Map<String, RealizationStatement> makeStatementLookup(
        List<? extends RealizationStatement> stmts)
    {
        Map<String, RealizationStatement> result = Maps.newHashMap();
        for (RealizationStatement stmt : stmts) {
            for (TraitSignature tsc : stmt.getTuplesRealized()) {
                if (tsc.isSingleton()) {
                    for (String name : tsc.getNamesOfDeclaredFormals()) {
                        result.put(name, stmt);
                    }
                }
                else {
                    String traitName = tsc.getName();
                    result.put(traitName, stmt);
                }
            }
        }
        return result;
//        return Functions.forMap(result);
    }

    // generates the sequence of singletons and other locals
    public static NodesWithLocations collectivelyGenerateLocals(
        List<RealizationStatement> stmts, final OptionMatchTable table,
        final Option destinationOption, final EntityDataBase edb) throws CoreException
    {
        final NodesWithLocations result = new NodesWithLocations();
        final OptionInterface optionIntf = destinationOption.getOptionInterface();
        final @ReadOnly Map<String, RealizationStatement> stmtLookup = makeStatementLookup(stmts);
        final Graph<String> dependencies = makeDependencyGraph(stmts, table, stmtLookup);

        Graph.LayeredVisitor<String, CoreException> generatePerLayer;
        generatePerLayer = new LayerAdaptor(stmts) {
            @Override public void visitLayer(List<String> layer) throws CoreException {
                List<RealizationStatement> stmts = Lists.newArrayList();
                for (String traitName : layer) {
                    // EXAMPLE: Note the subtle difference with containsKey versus
                    // apply(..) != null when it comes to the null key!
                    RealizationStatement stmt = stmtLookup.get(traitName);
                    if (stmt != null && !stmts.contains(stmt)) {
                        stmts.add(stmt);
                    }
                }
                for (RealizationStatement stmt : stmts) {
                    NodesWithLocations generatedLocal;
                    if (stmt.isSingletonRealization()) {
                        generatedLocal = stmt.generateSingleton(table, optionIntf);
                    }
                    else if (stmt.isLocal()) {
                        generatedLocal = stmt.generateLocals(table);
                    }
                    else if (stmt.isStatic()) {
                        realizeFixedPoint(edb, table, stmts);
                        continue;
                    }
                    else {
                        continue;
                    }
                    List<TraitValue> relations = generatedLocal.getLocations();
                    for (TraitValue hasARelation : relations) {
                        List<EntityTuple> entities = hasARelation.getEntities();
                        for (EntityTuple tuple : entities) {
                            ASTNode member = (ASTNode)tuple
                                .lookupEntity(EntityDataBase.CHILD_VAR_REF);
                            Object parent = tuple
                                .lookupEntity(EntityDataBase.PARENT_VAR_REF);
                            ITypeBinding typeBinding;
                            if (parent instanceof ITypeBinding) {
                                typeBinding = (ITypeBinding)parent;
                            }
                            else {
                                typeBinding = edb.lookupTypeBinding((AbstractTypeDeclaration)parent);
                            }
                            ASTUtil.recordNewParent(member, typeBinding);
                        }
                    }
                    result.merge(generatedLocal);
                }
            }
        };
        dependencies
            .iterateOverTopologicalLayers(new LayeredVisitor<String, RuntimeException>() {
                @Override public void cycleFound(List<String> cycle) {
                    System.out.printf("Panic!!!%n");
                }

                @Override public void visitLayer(List<String> layer) {
                    System.out.printf("Layer: %s%n%n", StringUtil.separate(layer));
                }
            });
        dependencies.iterateOverTopologicalLayers(generatePerLayer);
        return result;
    }

    private static void singletonTopologicalOrder(
        List<RealizationStatement> singletonStmts, OptionMatchTable table,
        SingletonOperation op) throws CoreException
    {
        ArrayList<RealizationStatement> toRealize = newArrayList(singletonStmts);
        // TASK -- code to re-move
//        Set<String> alreadyRealized = table.getSingletonNames();
        Set<String> alreadyRealized = Sets.newHashSet();
        int previousSize = toRealize.size() + 1;
        while (toRealize.size() != previousSize) {
            previousSize = toRealize.size();

            for (Iterator<RealizationStatement> i = toRealize.iterator(); i.hasNext();) {
                RealizationStatement singletonStmt = i.next();
                Set<String> refs = singletonStmt.expression.getArcumVariableReferences();
                Set<String> realizes = singletonStmt.getNamesRealizedBy();
                refs.removeAll(alreadyRealized);
                if (realizes.containsAll(refs)) {
                    op.apply(singletonStmt);
                    alreadyRealized.addAll(realizes);
                    i.remove();
                }
                else {
                    // MACNEIL: Come back to here: When a variable is misspelled it
                    // will be in the refs set but not the realizes set: Is there any
                    // other case we get here?
                    System.err.printf("Internal error or not?%n");
                }
            }
        }
        if (toRealize.size() != 0) {
            ArcumError.fatalError("Circular dependency among:%n<<%s>>%n", separate(
                toRealize, ">>>\n<<<"));
        }
    }

    private static boolean checkLocalsExist(EntityDataBase edb, NodesWithLocations nodes)
    {
        boolean localsExist = true;
        for (TraitValue ownershipRelation : nodes.getLocations()) {
            for (EntityTuple relation : ownershipRelation.getEntities()) {
                final Object parentEntity;
                final TypeDeclaration parent;
                final ASTNode newNode;

                parentEntity = relation.lookupEntity(PARENT_VAR_REF);
                if (parentEntity instanceof ITypeBinding) {
                    ITypeBinding typeBinding = (ITypeBinding)parentEntity;
                    parent = (TypeDeclaration)edb.lookupTypeDeclaration(typeBinding);
                }
                else {
                    parent = (TypeDeclaration)parentEntity;
                }
                newNode = (ASTNode)relation.lookupEntity(CHILD_VAR_REF);

                BodyDeclaration actualNode = findASTLocation(parent, newNode);
                if (actualNode == null) {
                    ArcumError.userError(new SourceLocation(parent),
                        "Expected to find %s in %s", Entity.getDisplayString(newNode),
                        Entity.getDisplayString(parent));
                    localsExist = false;
                }
            }
        }
        return localsExist;
    }

    // Are we a member of the given type?
    private static BodyDeclaration findASTLocation(TypeDeclaration type, ASTNode node) {
        for (Object obj : type.bodyDeclarations()) {
            BodyDeclaration member = (BodyDeclaration)obj;
            if (Entity.compareTo(node, member) == 0) {
                return member;
            }
        }
        return null;
    }

    private static void realizeFixedPoint(EntityDataBase edb, OptionMatchTable table,
        Collection<RealizationStatement> statements) throws CoreException
    {
        if (true || DEBUG) {
            System.out.printf("%nFixed-pointing:%n%s%n", StringUtil.separate(statements,
                format("%n")));
        }

        addTraits(statements, table);
        checkForValidNumberOfStatements(statements);

        if (statements.size() == 1) {
            RealizationStatement stmt = statements.iterator().next();
            if (stmt.isSingletonRealization()) {
                stmt.realizeSingleton(edb, table);
                return;
            }
            else if (stmt.isLocal()) {
                NodesWithLocations locals = stmt.generateLocals(table);
                if (!checkLocalsExist(edb, locals)) {
                    ArcumError.stop();
                }
                // and now fall through to let the regular realizeTrait handle it,
                // given that we know it will find the matches
            }
        }

        boolean updated = true;
        while (updated) {
            updated = false;
            for (RealizationStatement stmt : statements) {
                boolean addedNew = stmt.realizeTrait(edb, table);
                if (addedNew) {
                    updated = true;
                }
            }
        }
    }

    private static void addTraits(Collection<RealizationStatement> statements,
        OptionMatchTable table)
    {
        for (RealizationStatement stmt : statements) {
            if (!stmt.isSingletonRealization()) {
                if (stmt.tuplesRealized.size() != 1) {
                    ArcumError.fatalError("Internal error: should have one of these");
                }
                TraitSignature traitSignature = stmt.tuplesRealized.get(0);
                table.addTrait(traitSignature, stmt.isNested(), stmt.isStatic());
            }
        }
    }

    private static void checkForValidNumberOfStatements(
        Collection<RealizationStatement> statements)
    {
        if (statements.size() > 1) {
            // if there are more than one then they must all be non-singleton, non-local
            for (RealizationStatement stmt : statements) {
                if (stmt.isSingletonRealization() /*FRIDAY: check no longer needed? || stmt.isLocal()*/) {
                    ArcumError
                        .fatalError("Internal error: one singleton or local at a time");
                }
            }
        }
    }

    private Set<String> getNamesRealizedBy() {
        Set<String> result = new HashSet<String>();
        for (TraitSignature type : tuplesRealized) {
            result.addAll(type.getNamesOfDeclaredFormals());
        }
        return result;
    }

    private void realizeSingleton(EntityDataBase entityDataBase,
        OptionMatchTable optionMatchTable) throws CoreException
    {
        Satisfier satisfier = new Satisfier(expression);
        Collection<List<EntityTuple>> matches;
        matches = satisfier.getMatches(tuplesRealized, entityDataBase, optionMatchTable);

        List<EntityTuple> match = internalCheckAndExtractSingleton(optionMatchTable,
            matches, entityDataBase);
        for (EntityTuple tuple : match) {
            optionMatchTable.addSingleton(tuple);
        }
    }

    private List<EntityTuple> internalCheckAndExtractSingleton(OptionMatchTable lookup,
        Collection<List<EntityTuple>> matches, EntityDataBase edb)
    {
        if (matches.size() != 1) {
            String message = String.format("Expected one unique match,"
                + " but instead found %d matches", matches.size());
            SourceLocation location = lookup.getLocation();
            if (singletonErrorMessage != ErrorMessage.EMPTY_MESSAGE) {
                String reason = singletonErrorMessage.getMessage(lookup);
                message = String.format("%s (%s)", reason, message);
                if (singletonErrorMessage.hasLocation()) {
                    location = singletonErrorMessage.getLocation(lookup, edb, lookup);
                }
            }
            fatalUserError(location, message);
        }

        List<EntityTuple> match = matches.iterator().next();
        return match;
    }

    public static List<EntityTuple> checkAndExtractSingleton(
        OptionMatchTable optionMatchTable, Collection<List<EntityTuple>> matches)
    {
        // GETDONE -- the data given to this function is sometimes invalid
        if (matches.size() != 1) {
            fatalUserError(optionMatchTable.getLocation(),
                "Expected one unique match, but instead found %d matches", matches.size());
        }

        List<EntityTuple> match = matches.iterator().next();
        return match;
    }

    private NodesWithLocations generateSingleton(OptionMatchTable table,
        OptionInterface optionIntf)
    {
        Satisfier satisfier = new Satisfier(expression);
        NodesWithLocations singletons;
        singletons = satisfier.generateSingletons(tuplesRealized, table, optionIntf);
        for (EntityTuple singleton : singletons.getNodes()) {
            table.addSingleton(singleton);
        }
        return singletons;
    }

    private NodesWithLocations generateLocals(OptionMatchTable table) {
        Satisfier satisfier = new Satisfier(expression);
        NodesWithLocations locals;
        locals = satisfier.generateLocals(tuplesRealized, table);
        return locals;
    }

    public EntityTuple generateEntityReplacement(String name, EntityTuple entity,
        OptionMatchTable table, AST ast)
    {
        Satisfier satisfier = new Satisfier(expression);
        EntityTuple result;
        result = satisfier.generateEntityReplacement(tuplesRealized, table, entity, ast);
        table.addTraitInstance(name, result);
        return result;
    }

    // Assumes that typeCheckAndValidate has already been called. Returns true if
    // any new instances are added to the table.
    private boolean realizeTrait(EntityDataBase entityDataBase,
        OptionMatchTable optionMatchTable) throws CoreException
    {
        TraitSignature traitSignature = tuplesRealized.get(0);
        String name = traitSignature.getName();

        Satisfier satisfier = new Satisfier(expression);
        Collection<List<EntityTuple>> matches;
        matches = satisfier.getMatches(tuplesRealized, entityDataBase, optionMatchTable);

        boolean anyNew = false;
        for (List<EntityTuple> match : matches) {
            if (match.size() != 1) {
                ArcumError.fatalError("Internal error: should only have one trait type");
            }
            boolean addedNew = optionMatchTable.addTraitInstance(name, match.get(0));
            if (addedNew) {
                anyNew = true;
            }
        }
        return anyNew;
    }

    public boolean isStatic() {
        return false;
    }

    protected boolean isNested() {
        return false;
    }

    public SourceLocation getPosition() {
        return position;
    }

    public void setPosition(SourceLocation position) {
        this.position = position;
    }

    public List<TraitSignature> getTuplesRealized() {
        return tuplesRealized;
    }

    public void verifyValidVariables() {
        // we can't do this because the "any" variable is not constructive: we
        // wouldn't know what to generate for it
        Set<String> vars = expression.getArcumVariableReferences();
        if (vars.contains(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE)) {
            ArcumError.fatalUserError(position, "Cannot use special \"_\" variable in"
                + " a non-static realization statement");
        }
    }

    public void checkUserDefinedPredicates(List<TraitSignature> signatures) {
        Set<String> varsInScope = TraitSignature.getGlobals(signatures);
        for (TraitSignature signature : this.tuplesRealized) {
            varsInScope.addAll(signature.getNamesOfDeclaredFormals());
        }
        expression.checkUserDefinedPredicates(signatures, varsInScope);
        // EXAMPLE: Yet ANOTHER bug, did not descend to requireClauses and messages!
        // Could their absence have been detected automatically with the visitor
        // pattern?
        for (ConstraintExpression requireClause : requireClauses) {
            requireClause.checkUserDefinedPredicates(signatures, varsInScope);
        }
        for (ErrorMessage message : messages) {
            message.checkUserDefinedPredicates(signatures, varsInScope);
        }
    }

    protected ConstraintExpression getExpression() {
        return expression;
    }
}