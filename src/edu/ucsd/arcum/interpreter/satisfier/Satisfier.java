package edu.ucsd.arcum.interpreter.satisfier;

import static com.google.common.collect.Lists.transform;
import static edu.ucsd.arcum.ArcumPlugin.DEBUG;
import static edu.ucsd.arcum.interpreter.ast.FormalParameter.getIdentifier;
import static edu.ucsd.arcum.interpreter.ast.RealizationStatement.checkAndExtractSingleton;
import static edu.ucsd.arcum.interpreter.fragments.ProgramFragmentFactory.EMBEDDED_VALUE_PREFIX;

import java.util.*;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.exceptions.Unreachable;
import edu.ucsd.arcum.interpreter.ast.*;
import edu.ucsd.arcum.interpreter.ast.expressions.*;
import edu.ucsd.arcum.interpreter.fragments.ProgramFragment;
import edu.ucsd.arcum.interpreter.fragments.ProgramFragmentFactory;
import edu.ucsd.arcum.interpreter.fragments.ResolvedEntity;
import edu.ucsd.arcum.interpreter.fragments.SubtreeList;
import edu.ucsd.arcum.interpreter.parser.ArcumStructureParser.EmbeddedExpression;
import edu.ucsd.arcum.interpreter.query.*;

public class Satisfier
{
    private final ConstraintExpression expression;
    private TypeLookupTable savedTypesForCallback;
    // EXAMPLE: edb and ast are possible examples of a union or either/or kind of selection idiom
    // (although in the current form, only edb can be null, due to the <..> feature)
    // edb may be null
    private EntityDataBase edb;
    private OptionMatchTable table;
    public AST ast;

    public Satisfier(ConstraintExpression expression) {
        this.expression = expression;
    }

    public Collection<List<EntityTuple>> getMatches(List<TraitSignature> traitSignature,
        EntityDataBase edb, OptionMatchTable optionMatchTable)
    {
        BindingsSet set;
        Collection<List<EntityTuple>> entityTuples;

        clearGlobals();
        try {
            TypeLookupTable types = new TypeLookupTable(traitSignature);
            this.edb = edb;
            this.table = optionMatchTable;
            this.ast = newAST();
            BindingMap bindingMap = optionMatchTable.extractAsBindings();
            set = sat(expression, types, EntityType.ERROR, bindingMap, null);
            entityTuples = set.extractAsEntityTuples(traitSignature);
            return entityTuples;
        }
        finally {
            clearGlobals();
        }
    }

    public NodesWithLocations generateSingletons(List<TraitSignature> tuplesRealized,
        OptionMatchTable optionMatchTable, OptionInterface optionIntf)
    {
        final BindingMap bindingMap;
        final BindingsSet set;
        final Collection<List<EntityTuple>> entityTuples;
        final List<EntityTuple> singletons;
        final List<EntityTuple> optionLevelSingletons;
        final List<TraitValue> builtIns;
        final NodesWithLocations result;

        clearGlobals();
        try {
            TypeLookupTable types = new TypeLookupTable(tuplesRealized);
            this.edb = null;
            this.table = optionMatchTable;
            this.ast = newAST();
            bindingMap = optionMatchTable.extractAsBindings();
            set = sat(expression, types, EntityType.ERROR, bindingMap, null);
            entityTuples = set.extractAsEntityTuples(tuplesRealized);
            singletons = checkAndExtractSingleton(optionMatchTable, entityTuples);
            optionLevelSingletons = Lists.newArrayList();
            BindingMap onlyEntry = set.iterator().next();
            builtIns = onlyEntry.extractBuiltInTraits();

            for (EntityTuple singleton : singletons) {
                String anonymousOrNamedAbstract = singleton.getType().getName();
                if (!optionIntf.hasSingletonMember(anonymousOrNamedAbstract)) {
                    optionLevelSingletons.add(singleton);
                }
            }
            result = new NodesWithLocations(optionLevelSingletons, builtIns);
            return result;
        }
        finally {
            clearGlobals();
        }
    }

    public NodesWithLocations generateLocals(List<TraitSignature> tuplesRealized,
        OptionMatchTable optionMatchTable)
    {
        final BindingMap bindingMap;
        final BindingsSet set;
        final Collection<List<EntityTuple>> entityTuples;
        final List<EntityTuple> locals;
        final List<TraitValue> builtIns;
        final NodesWithLocations result;

        clearGlobals();
        try {
            TypeLookupTable types = new TypeLookupTable(tuplesRealized);
            this.edb = null;
            this.table = optionMatchTable;
            this.ast = newAST();
            bindingMap = optionMatchTable.extractAsBindings();
            set = sat(expression, types, EntityType.ERROR, bindingMap, null);
            entityTuples = set.extractAsEntityTuples(tuplesRealized);

            locals = Lists.newArrayList();
            for (List<EntityTuple> entityTupleList : entityTuples) {
                if (entityTupleList.size() != 1) {
                    ArcumError.fatalError("Internal error: should only have one");
                }
                EntityTuple entityTuple = entityTupleList.get(0);
                locals.add(entityTuple);
            }

            builtIns = Lists.newArrayList();
            for (BindingMap generated : set) {
                builtIns.addAll(generated.extractBuiltInTraits());
            }
            result = new NodesWithLocations(locals, builtIns);
            return result;
        }
        finally {
            clearGlobals();
        }
    }

    public EntityTuple generateEntityReplacement(List<TraitSignature> tuplesRealized,
        OptionMatchTable optionMatchTable, EntityTuple entity, AST ast)
    {
        final BindingMap bindingMap;
        final BindingMap traitBindings;
        final BindingsSet set;
        final Collection<List<EntityTuple>> entityTuples;
        final List<EntityTuple> singletons;
        final List<TraitValue> builtIns;

        clearGlobals();
        try {
            TypeLookupTable types = new TypeLookupTable(tuplesRealized);
            this.edb = null;
            this.table = optionMatchTable;
            this.ast = ast;
            bindingMap = optionMatchTable.extractAsBindings();
            Map<String, Object> values = Maps.newHashMap(entity.getValues());
            ASTNode rootNode = entity.getRootNode();
            String rootName = null;
            Iterator<Entry<String, Object>> it = values.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Object> entry = it.next();
                rootName = entry.getKey();
                Object o = entry.getValue();
                if (o == rootNode) {
                    it.remove();
                    break;
                }
            }
            traitBindings = new BindingMap(values);
            bindingMap.addBindings(traitBindings);
            set = sat(expression, types, EntityType.ERROR, bindingMap, null);
            entityTuples = set.extractAsEntityTuples(tuplesRealized);
            singletons = checkAndExtractSingleton(optionMatchTable, entityTuples);

            EntityTuple unrooted = Iterables.getOnlyElement(singletons);
            values = unrooted.getValues();
            Object rootValue = values.get(rootName);
            ASTNode root = rootValue instanceof ASTNode ? (ASTNode)rootValue : null;
            EntityTuple replacement = new EntityTuple(unrooted.getType(), values, root);
            return replacement;
        }
        finally {
            clearGlobals();
        }
    }

    // Evaluates only (returns a Boolean result instead of a list of matches)
    public boolean evaluate(IEntityLookup lookup, EntityDataBase edb,
        OptionMatchTable symTab)
    {
        clearGlobals();
        try {
            TypeLookupTable types = lookup.getTypeLookupTable();
            this.edb = edb;
            this.table = symTab;
            this.ast = newAST();
            BindingMap bindingMap = lookup.extractAsBindings();
            BindingsSet sat = sat(expression, types, EntityType.ERROR, bindingMap, null);
            boolean result = representsTrue(sat, bindingMap);
            return result;
        }
        finally {
            clearGlobals();
        }
    }

    public Object evaluateEntityValue(IEntityLookup lookup, EntityDataBase edb,
        OptionMatchTable symTab)
    {
        clearGlobals();
        try {
            TypeLookupTable types = new TypeLookupTable(new ArrayList<TraitSignature>());
            this.edb = edb;
            this.table = symTab;
            this.ast = newAST();
            BindingMap bindingMap = lookup.extractAsBindings();
            BindingsSet sat = sat(expression, types, EntityType.ERROR, bindingMap, null);
            for (BindingMap map : sat) {
                return map.getResult();
            }
            return null;
        }
        finally {
            clearGlobals();
        }
    }

    private boolean representsTrue(BindingsSet exprEvaluation, BindingMap originalInput) {
        return !exprEvaluation.isEquivalentTo(BindingsSet.newEmptySet());
    }

    // matches against something already found in "in" or literal values (such as
    // Java class names)
    public BindingsSet immediateMatchingSat(ConstraintExpression phi,
        EntityType expectedType, BindingMap in)
    {
        if (savedTypesForCallback == null) {
            ArcumError.fatalError("Invalid callback");
        }
        final EntityDataBase realEDB = this.edb;
        try {
            EntityDataBase unpopulatedEDB = new EntityDataBase(null);
            this.edb = unpopulatedEDB;
            return sat(phi, savedTypesForCallback, expectedType, in, null);
        }
        finally {
            this.edb = realEDB;
        }
    }

    private void debugOutput(BindingMap in, String varName) {
        Object entity = in.lookupEntity(varName);
        if (entity != null) {
            System.out.printf("%s == %s%n", varName, ASTUtil.getDebugString(entity));
        }
    }

    // If the given entity data-base is null, then we generate matching entities.
    // Otherwise, we return actual entities in the program that match.
    private BindingsSet sat(ConstraintExpression phi, TypeLookupTable types,
        EntityType expectedType, BindingMap in, Object knownEntity)
    {
        final boolean matchingMode = (edb != null);
        if (phi instanceof UnificationExpression) {
            UnificationExpression unifyExpr = (UnificationExpression)phi;
            String lhsName = unifyExpr.getName();
            Object lhsValue = in.lookupEntity(lhsName);
            ConstraintExpression rhs = unifyExpr.getRightHandSide();
            EntityType type = types.lookupType(lhsName);
            BindingsSet result = BindingsSet.newEmptySet();
            if (rhs instanceof VariableReferenceExpression) {
                VariableReferenceExpression vre = (VariableReferenceExpression)rhs;
                String rhsName = vre.getName();
                Object rhsValue = in.lookupEntity(rhsName);
                System.out.printf("It's the special case: %s (%s), %s (%s)%n", lhsName,
                    Entity.getDisplayString(lhsValue), rhsName, Entity
                        .getDisplayString(rhsValue));
                // TUESDAY ...
                if (lhsValue == null && rhsValue == null) {
                    // Bind both vars to all instances of the most specific type
                }
                else if (lhsValue != null && rhsValue != null) {
                    // Compare values: if equal including locations, return the
                    // binding we started with
                    if (Entity.compareToWithLocations(lhsValue, rhsValue) == 0) {
                        result.addEntry(in);
                    }
                }
                else {
                    // Unify the unbound var with the already bound one
                    String unboundName = (lhsValue == null) ? lhsName : rhsName;
                    Object boundValue = (lhsValue == null) ? rhsValue : lhsValue;
                    in.bind(unboundName, boundValue, type);
                    result.addEntry(in);
                }
            }
            else {
                BindingsSet sat = sat(rhs, types, type, in, lhsValue);
                for (BindingMap theta : sat) {
                    Object otherEntity = theta.getResult();
                    if (lhsValue != null && otherEntity != lhsValue) {
                        continue;
                    }
                    theta.bindResultAs(lhsName);
                    result.addEntry(theta);
                }
            }
//            System.out.printf("Unification %s%nWith binding map:%n %s,%nReturns: %s%n%n",
//                unifyExpr, in, result);
            return result;
        }
        else if (phi instanceof PatternExpression) {
            PatternExpression patternExpr = (PatternExpression)phi;
            CurrentBindingsLookup lookup = new CurrentBindingsLookup(table, in);

            if (patternExpr.hasEmbeddedExpressions()) {
                BindingMap embeddedBindings = BindingMap.newEmptyMap();

                // evaluate and collapse all embedded expressions first
                List<EmbeddedExpression> embeds = patternExpr.getEmbeddedExpressions();
                for (int embedIndex = 0; embedIndex < embeds.size(); ++embedIndex) {
                    EmbeddedExpression embed = embeds.get(embedIndex);
                    FormalParameter boundVar = embed.getBoundVar();
                    ConstraintExpression embeddedExpr = embed.getConstraintExpression();

                    TypeLookupTable nextScope = TypeLookupTable.newScope(types, boundVar);
                    BindingsSet bigTheta = sat(embeddedExpr, nextScope, EntityType.ERROR,
                        in, knownEntity);

                    String projectionVar = boundVar.getIdentifier();
                    List<ProgramFragment> members = Lists.newArrayList();
                    for (BindingMap theta : bigTheta) {
                        Object entity = theta.lookupEntity(projectionVar);
                        ProgramFragment member = ResolvedEntity.newInstance(entity);
                        members.add(member);
                    }
                    SubtreeList set = new SubtreeList(members, SubtreeList.Kind.UNORDERED);
                    String setKey = String.format("%s%d", EMBEDDED_VALUE_PREFIX,
                        embedIndex);
                    embeddedBindings.bind(setKey, set, EntityType.PUNT);
                }
                lookup = new CurrentBindingsLookup(lookup, embeddedBindings);
            }

            BindingsSet result;
            if (matchingMode && !patternExpr.isImmediatePattern()) {
                if (knownEntity == null) {
                    result = edb.enumerateMatchingBindings(patternExpr, expectedType, in,
                        lookup, types);
                }
                else {
                    result = edb.immeditateMatchingBinding(patternExpr, expectedType, in,
                        lookup, types, knownEntity);
                }
            }
            else {
                final ProgramFragmentFactory builder;
                final List<ProgramFragment> fragments;

                builder = new ProgramFragmentFactory(patternExpr, expectedType, lookup,
                    types, false);
                fragments = builder.getAbstractProgramFragments();

                result = BindingsSet.newEmptySet();

                for (ProgramFragment fragment : fragments) {
                    BindingMap theta;
                    if (fragment.isResolved()) {
                        theta = fragment.matchResolvedEntity();
                    }
                    else {
                        theta = fragment.generateNode(lookup, ast);
                    }
                    if (theta != null) {
                        Object rootEntity = theta.getResult();
                        if (rootEntity == null) {
                            ArcumError.fatalError("This shouldn't happen");
                        }
                        BindingMap merge = in.consistentMerge(theta);
                        if (merge != null) {
                            BindingMap mergedWithRoot = new BindingMap(rootEntity);
                            mergedWithRoot.addBindings(merge);
                            result.addEntry(mergedWithRoot);
                        }
                    }
                }
            }
            return result;
        }
        else if (phi instanceof BooleanDisjunction) {
            BooleanDisjunction disjunction = (BooleanDisjunction)phi;
            Collection<? extends ConstraintExpression> disjuncts;
            disjuncts = disjunction.getClauses();
            // an empty disjunct is equivalent to false
            BindingsSet result = BindingsSet.newEmptySet();
            for (ConstraintExpression disjunct : disjuncts) {
                BindingsSet sat = sat(disjunct, types, expectedType, in, knownEntity);
                result = result.union(sat);
            }
            return result;
        }
        else if (phi instanceof BooleanConjunction) {
            // For each conjunction, collect the newly declared variables and treat
            // the conjunction as if it were in an "exists"
            BooleanConjunction conjunction = (BooleanConjunction)phi;
            Collection<? extends ConstraintExpression> conjuncts;
            conjuncts = conjunction.getClauses();
            if (conjuncts.isEmpty()) {
                // an empty conjunct is equivalent to true
                return trueResult(in);
            }
            else {
                Iterator<? extends ConstraintExpression> iterator = conjuncts.iterator();
                ConstraintExpression conjunct = iterator.next();
                BindingsSet bigTheta = sat(conjunct, types, expectedType, in, knownEntity);
                while (iterator.hasNext()) {
                    conjunct = iterator.next();
                    BindingsSet result = BindingsSet.newEmptySet();
                    for (BindingMap theta : bigTheta) {
                        BindingsSet sat = sat(conjunct, types, expectedType, theta,
                            knownEntity);
                        result = result.union(sat);
                    }
                    bigTheta = result;
                }
                return bigTheta;
            }
        }
        else if (phi instanceof BooleanEquivalence) {
            BooleanEquivalence equivalence = (BooleanEquivalence)phi;
            Collection<ConstraintExpression> terms = equivalence.getClauses();
            Iterator<ConstraintExpression> iterator = terms.iterator();
            ConstraintExpression term = iterator.next();
            BindingsSet first = sat(term, types, expectedType, in, knownEntity);
            while (iterator.hasNext()) {
                term = iterator.next();
                BindingsSet current = sat(term, types, expectedType, in, knownEntity);
                if (!first.isEquivalentTo(current)) {
                    return falseResult(in);
                }
            }
            return trueResult(in);
        }
        else if (phi instanceof TrueLiteral) {
            return trueResult(in);
        }
        else if (phi instanceof FalseLiteral) {
            return falseResult(in);
        }
        else if (phi instanceof BooleanNegation) {
            BooleanNegation negation = (BooleanNegation)phi;
            ConstraintExpression operand = negation.getOperand();
            BindingsSet sat = sat(operand, types, expectedType, in, knownEntity);
            if (representsTrue(sat, in)) {
                return falseResult(in);
            }
            else {
                return trueResult(in);
            }
        }
        else if (phi instanceof FunctionalExpression) {
            FunctionalExpression func = (FunctionalExpression)phi;
            CurrentBindingsLookup lookup = new CurrentBindingsLookup(table, in);
            TypeLookupTable prevValue = this.savedTypesForCallback;
            try {
                this.savedTypesForCallback = types;
                BindingsSet result = func.evaluate(in, lookup, this, matchingMode);
//                System.out.printf("Function %s%nWith binding map:%n %s,%nReturns: %s%n%n",
//                    func, in, result);
                return result;
            }
            finally {
                this.savedTypesForCallback = prevValue;
            }
        }
        else if (phi instanceof VariableReferenceExpression) {
            VariableReferenceExpression refExpr = (VariableReferenceExpression)phi;
            String varName = refExpr.getName();
            Object entity = in.lookupEntity(varName);
            if (entity == null) {
                if (refExpr.isSpecialAnyVariable()) {
                    entity = new VariablePlaceholder(refExpr.getPosition());
                }
                else if (types.hasInformationFor(varName)) {
                    entity = new VariablePlaceholder(refExpr, types.lookupType(varName));
                }
                else {
                    // May have been a typo, in which case it's a user error, not a
                    // match not found
                    ArcumError.fatalUserError(phi.getPosition(),
                        "Variable \"%s\" has not been declared or is not in scope",
                        varName);
                    throw new Unreachable();
                }
            }
            BindingsSet result = singleResult(in, entity);
            return result;
        }
        else if (phi instanceof UniversalQuantifier) {
            UniversalQuantifier forallExpr = (UniversalQuantifier)phi;
            List<FormalParameter> boundVars = forallExpr.getBoundVars();
            TypeLookupTable nextScope = TypeLookupTable.newScope(types, boundVars);
            ConstraintExpression initExpr = forallExpr.getInitialSet();
            BindingsSet bigTheta = sat(initExpr, nextScope, EntityType.ERROR, in,
                knownEntity);
            ConstraintExpression body = forallExpr.getBody();

            boolean allTrue = true;
            eachElementCheck: for (BindingMap theta : bigTheta) {
                if (DEBUG) {
                    List<String> entityReps = Lists.newArrayList();
                    for (FormalParameter boundVar : boundVars) {
                        String id = boundVar.getIdentifier();
                        Object entity = theta.lookupEntity(id);
                        entityReps.add(String.format("%s=%s", id, Entity
                            .getDisplayString(entity)));
                    }
                    System.err.printf("Now checking: %s on %s%n", entityReps, body);
                }
                BindingsSet sat = sat(body, nextScope, expectedType, theta, knownEntity);
                if (!representsTrue(sat, theta)) {
                    allTrue = false;
                    if (forallExpr.hasErrorMessage()) {
                        ErrorMessage message = forallExpr.getErrorMessage();
                        CurrentBindingsLookup lookup = new CurrentBindingsLookup(table,
                            theta);
                        String text = message.getMessage(lookup);
                        FormalParameter rootVar = boundVars.get(0);
                        SourceLocation location;
                        if (message.hasLocation()) {
                            location = message.getLocation(lookup, edb, table);
                        }
                        else {
                            Object root = lookup.lookupEntity(rootVar.getIdentifier());
                            location = SourceLocation.fromEntity(root, edb);
                        }
                        ArcumError.userError(location, "%s", text);
                    }
                    else {
                        // short-circuit: no detailed error messages are needed, so
                        // we don't have to continue
                        break eachElementCheck;
                    }
                }
            }
            return (allTrue) ? trueResult(in) : falseResult(in);
        }
        else if (phi instanceof ExistentialQuantifier) {
            ExistentialQuantifier existsExpr = (ExistentialQuantifier)phi;
            List<FormalParameter> boundVars = existsExpr.getBoundVars();
            Set<String> varNames = Sets.newHashSet(transform(boundVars, getIdentifier));
            TypeLookupTable nextScope = TypeLookupTable.newScope(types, boundVars);

            ConstraintExpression body = existsExpr.getBody();
            BindingsSet bigTheta = sat(body, nextScope, EntityType.ERROR, in, knownEntity);
            BindingsSet result = BindingsSet.newEmptySet();
            for (BindingMap theta : bigTheta) {
                result.addEntry(theta.withVarsRemoved(varNames));
            }
            return result;
        }
        else if (phi instanceof SelectExpression) {
            SelectExpression condExpr = (SelectExpression)phi;
            List<ConstraintExpression> conditions = condExpr.getConditions();
            List<ConstraintExpression> values = condExpr.getValues();
            Iterator<ConstraintExpression> valueIter = values.iterator();
            BindingsSet result = BindingsSet.newEmptySet();
            boolean allConditionsFailed = true;
            for (ConstraintExpression condition : conditions) {
                ConstraintExpression value = valueIter.next();
                BindingsSet conditionResult = sat(condition, types, expectedType, in,
                    knownEntity);
                if (representsTrue(conditionResult, in)) {
                    allConditionsFailed = false;
                    BindingsSet conditionValue = sat(value, types, expectedType, in,
                        knownEntity);
                    result = result.union(conditionValue);
                }
            }
            if (allConditionsFailed) {
                ConstraintExpression elseValue = valueIter.next();
                result = sat(elseValue, types, expectedType, in, knownEntity);
            }
            return result;
        }
        else {
            ArcumError.fatalError("Can't handle this case: %s%n", phi.getClass());
            return null;
        }
    }

    public static BindingsSet singleResult(BindingMap in, Object entity) {
        BindingMap theta = new BindingMap(entity);
        theta = theta.consistentMerge(in);
        BindingsSet result = BindingsSet.newSet(theta);
        return result;
    }

    public static BindingsSet trueResult(BindingMap in) {
        return BindingsSet.newSet(in);
    }

    public static BindingsSet falseResult(BindingMap in) {
        return BindingsSet.newEmptySet();
    }

    private void clearGlobals() {
        this.savedTypesForCallback = null;
        this.edb = null;
        this.table = null;
        this.ast = null;
    }

    private AST newAST() {
        return AST.newAST(AST.JLS3);
    }
}