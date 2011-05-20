package edu.ucsd.arcum.interpreter.query;

import static com.google.common.collect.Lists.newArrayList;
import static edu.ucsd.arcum.ArcumPlugin.DEBUG;
import static edu.ucsd.arcum.interpreter.ast.ASTUtil.PARAMETER_NAME;
import static edu.ucsd.arcum.interpreter.ast.ASTUtil.find;
import static edu.ucsd.arcum.interpreter.ast.RealizationStatement.collectivelyGenerateLocals;
import static edu.ucsd.arcum.interpreter.ast.RealizationStatement.collectivelyRealizeStatements;

import java.io.PrintStream;
import java.util.*;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.exceptions.Unreachable;
import edu.ucsd.arcum.interpreter.ast.*;
import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;
import edu.ucsd.arcum.interpreter.fragments.Union;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.satisfier.CurrentBindingsLookup;
import edu.ucsd.arcum.interpreter.satisfier.NodesWithLocations;
import edu.ucsd.arcum.interpreter.satisfier.TypeLookupTable;
import edu.ucsd.arcum.interpreter.transformation.ResolvedConceptMapEntry;
import edu.ucsd.arcum.util.SystemUtil;

public class OptionMatchTable implements IEntityLookup
{
    private final ArcumDeclarationTable table;
    private Option option;
    private SourceLocation location;
    private final Map<String, TraitValue> singletons = new HashMap<String, TraitValue>();
    private final Map<String, TraitValue> traits = new HashMap<String, TraitValue>();
    private final Map<String, TraitValue> builtInTraits = new HashMap<String, TraitValue>();

    private Set<FormalParameter> resolvedVariables = null;
    private boolean needsUpdate = false;

    // constructor to use for matchAllEntities -- the location passed in is the
    // map entry that causes this entity table to exist in the first place, thus,
    // error messages should be associated with it if no other information is
    // available (e.g., when a search for a singleton finds no matches)
    public OptionMatchTable(ArcumDeclarationTable table, ResolvedConceptMapEntry rcme) {
        this.table = table;
        this.option = rcme.getOption();
        this.location = rcme.getLocation();

        TraitValue constructor = constructParameters(table, option, rcme.getArguments());
        singletons.put(option.getName(), constructor);
    }

    // constructor to use for generateSingletonEntities
    public OptionMatchTable(ArcumDeclarationTable table, Option originalOption,
        Option alternativeOption, OptionMatchTable entities) throws CoreException
    {
        this.table = table;
        this.option = alternativeOption;

        OptionInterface optionInterface = option.getOptionInterface();
        for (Map.Entry<String, TraitValue> entry : entities.singletons.entrySet()) {
            String singletonName = entry.getKey();
            TraitValue traitValue = entry.getValue();
            if (singletonName.equals(originalOption.getName())) {
                singletonName = alternativeOption.getName();
                TraitSignature type = constructorType(table, alternativeOption);
                TraitValue constructor = new TraitValue(singletonName, type);
                EntityTuple singleton = traitValue.getSingleton();
                Map<String, Object> values = singleton.getValues();
                ;
                EntityTuple tuple = new EntityTuple(type, values, null);
                constructor.addTuple(tuple);
                singletons.put(singletonName, constructor);
            }
            else if (optionInterface.lookupTupleSet(singletonName) != null) {
                singletons.put(singletonName, traitValue);
            }
        }

        List<RealizationStatement> stmts = optionInterface.getRealizationStatements();
        for (Map.Entry<String, TraitValue> entry : entities.traits.entrySet()) {
            String traitName = entry.getKey();
            TraitValue traitValue = entry.getValue();
            if (traitValue.isStatic()) {
                traits.put(traitName, traitValue);
            }
        }

        EntityDataBase entityDataBase = table.getEntityDataBase();
//        this.builtInTraits.putAll(entities.builtInTraits);
        importBuiltInTraitPredicates(entityDataBase);
        matchAllArgumentTraits(entityDataBase);
    }

    // match all trait arguments, singletons, and non-singleton entities
    public void matchAllEntities(EntityDataBase entityDataBase) throws CoreException {
        try {
            EntityDataBase.pushCurrentDataBase(entityDataBase);

            importBuiltInTraitPredicates(entityDataBase);

            matchAllArgumentTraits(entityDataBase);
            matchAllRealizationStatements(entityDataBase, option.getOptionInterface());

            if (true || DEBUG) {
                for (PrintStream stream : newArrayList(System.out, SystemUtil
                    .getOutStream()))
                {
                    stream.println(this.toString());
                    stream.flush();
                }
            }
        }
        finally {
            EntityDataBase.popMostRecentDataBase();
        }
    }

    private void importBuiltInTraitPredicates(EntityDataBase entityDataBase) {
        entityDataBase.insertBuiltInTraitValues(this);
    }

    private void matchAllArgumentTraits(EntityDataBase edb) throws CoreException {
        TraitValue traitValue = singletons.get(option.getName());
        List<EntityTuple> singletonList = traitValue.getEntities();
        EntityTuple tuple = singletonList.get(0);
        Collection<Object> entities = tuple.getValues().values();
        for (@Union("Entity")
        Object entity : entities)
        {
            if (entity instanceof MapTraitArgument) {
                MapTraitArgument mta = (MapTraitArgument)entity;
                mta.initializeValue(edb, option, this);
            }
        }

        // check parameter requirements first because, if these fail, then any error
        // messages that would come later might be misleading
        TraitSignature traitType = traitValue.getTraitType();
        boolean passed = checkEntityTupleConditions(tuple, traitType, edb, this);
        if (!passed) {
            ArcumError.stop();
        }
    }

    private void matchAllRealizationStatements(EntityDataBase entityDataBase,
        OptionInterface optionInterface) throws CoreException
    {
        List<RealizationStatement> stmts = Lists.newArrayList();
        stmts.addAll(optionInterface.getRealizationStatements());
        for (RealizationStatement stmt : stmts) {
            if (!stmt.isStatic()) {
                ArcumError.fatalUserError(stmt.getPosition(), "Interface level"
                    + " realization statements must be declared static");
            }
            stmt.typeCheckAndValidate(optionInterface);
        }
        stmts.addAll(option.getRealizationStatements());
        collectivelyRealizeStatements(stmts, entityDataBase, this);
    }

    // TASK -- generate all locals, not just singletons
    // generate only local entities
    public NodesWithLocations generateLocalEntities() throws CoreException {
        List<RealizationStatement> singletonStmts;
        singletonStmts = option.getRealizationsOfLocals();
        NodesWithLocations result = collectivelyGenerateLocals(singletonStmts, this,
            option, table.getEntityDataBase());
        List<EntityTuple> nodes = result.getNodes();
        for (EntityTuple node : nodes) {
            TraitSignature type = node.getType();
            String name = type.getName();
            if (!traits.containsKey(name)) {
                addTrait(type, false, type.isStaticDefinition());
            }
            addTraitInstance(name, node);
        }
        return result;
    }

    public EntityTuple generateEntityReplacement(EntityTuple entity) {
        TraitSignature type = entity.getType();
        String name = type.getName();
        ASTNode rootNode = entity.getRootNode();
        AST ast = rootNode.getAST();
        RealizationStatement realizationStmt = option.getStatementForReplacement(name);
        return realizationStmt.generateEntityReplacement(name, entity, this, ast);
    }

    public void addSingleton(EntityTuple singleton) {
        String name = singleton.getType().getName();

        TraitValue set = singletons.get(name);
        if (set != null) {
            EntityTuple alreadyThere = set.getSingleton();
            System.err.printf("Internal error: Adding %s, which already has"
                + " entry %s (parent=%s)%n", name, alreadyThere, alreadyThere
                .getRootNode().getParent());
        }
        set = new TraitValue(name, singleton.getType());
        set.addTuple(singleton);
        singletons.put(name, set);
        this.needsUpdate = true;
    }

    public void addTrait(TraitSignature type, boolean isNested, boolean isStatic) {
        String name = type.getName();
        TraitValue set = new TraitValue(name, type, isStatic, isNested);
        traits.put(name, set);
    }

    public void addBuiltInTrait(TraitSignature type) {
        String name = type.getName();
        TraitValue set = new TraitValue(name, type, false, true);
        builtInTraits.put(name, set);
    }

    // Returns true if this set did not already contain the specified element
    public boolean addTraitInstance(String name, EntityTuple tuple) {
        TraitValue set = traits.get(name);
        if (set == null) {
            set = builtInTraits.get(name);
        }
        return set.addTuple(tuple);
    }

    private static TraitValue constructParameters(ArcumDeclarationTable table,
        Option option, List<MapNameValueBinding> arguments)
    {
        TraitSignature type = constructorType(table, option);
        Map<String, Object> values = new TreeMap<String, Object>();
        for (MapNameValueBinding binding : arguments) {
            values.put(binding.getName(), binding.getValue());
        }
        OptionInterface parent = option.getOptionInterface();
        TraitSignature parentType = parent.lookupTupleSet(parent.getName());
        TraitValue result = new TraitValue(option.getName(), type);
        if (parentType != null) {
            List<ConstraintExpression> conditions = parentType.getRequireClauses();
            List<ErrorMessage> messages = parentType.getErrorMessages();
            RealizationStatement.checkPairConsistency(conditions, messages);
            for (int i = 0; i < conditions.size(); ++i) {
                ConstraintExpression requires = conditions.get(i);
                ErrorMessage errorMessage = messages.get(i);
                type.addRequiresClause(requires, errorMessage);
            }
            EntityTuple tuple = new EntityTuple(type, values, null);
            result.addTuple(tuple);
        }
        return result;
    }

    private static TraitSignature constructorType(ArcumDeclarationTable table,
        Option option)
    {
        List<FormalParameter> params = option.getSingletonParameters(table);
        TraitSignature type = TraitSignature.makeSingleton(option.getName(), params);
        return type;
    }

    public Set<FormalParameter> getResolvedVariables() {
        if (this.resolvedVariables == null || this.needsUpdate) {
            this.resolvedVariables = new HashSet<FormalParameter>();
            this.needsUpdate = false;
            for (TraitValue trait : singletons.values()) {
                EntityTuple singleton = trait.getSingleton();
                resolvedVariables.addAll(singleton.getBoundVariables());
            }
        }
        return resolvedVariables;
    }

    @Override
    public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("Matched program entities for ");
        buff.append(option.getName());
        buff.append(":");
        buff.append(String.format("%nSingletons"));
        for (TraitValue trait : singletons.values()) {
            EntityTuple singleton = trait.getSingleton();
            buff.append(String.format("%n --"));
            buff.append(singleton.toString());
        }
        buff.append(String.format("%nTraits"));
        for (TraitValue trait : traits.values()) {
            List<EntityTuple> entities = trait.getEntities();
            for (EntityTuple entity : entities) {
                buff.append(String.format("%n --"));
                buff.append(entity.toString());
            }
        }
        if (DEBUG) {
            buff.append(String.format("%nBuilt-ins"));
            for (TraitValue trait : builtInTraits.values()) {
                List<EntityTuple> nonSingletonList = trait.getEntities();
                for (EntityTuple nonSingleton : nonSingletonList) {
                    buff.append(String.format("%n --"));
                    buff.append(nonSingleton.toString());
                }
            }
            buff.append(String.format("%n"));
        }
        return buff.toString();
    }

    public boolean resolvesSingleton(String ref) {
        return findResolvedSingleton(ref) != null;
    }

    @Override
    public FormalParameter findResolvedSingleton(String variableName) {
        return find(variableName, getResolvedVariables(), PARAMETER_NAME);
    }

    public @Union("Entity")
    Object getSingletonEntity(String var) {
        @Union("Entity")
        Object entity = null;
        for (TraitValue trait : singletons.values()) {
            EntityTuple singleton = trait.getSingleton();
            entity = singleton.lookupEntity(var);
            if (entity != null)
                break;
        }
        if (entity == null) {
            ArcumError.fatalError("The variable %s doesn't exist, or something%n", var);
        }
        return entity;
    }

    @Override
    public @Union("Entity")
    Object lookupEntity(String reference) {
        // look it up in the singletons first
        for (TraitValue traitValue : singletons.values()) {
            for (EntityTuple tuple : traitValue.getEntities()) {
                @Union("Entity")
                Object entity = tuple.lookupEntity(reference);
                if (entity != null) {
                    return entity;
                }
            }
        }
        // and then return if a trait matches
        for (TraitValue traitValue : traits.values()) {
            if (reference.equals(traitValue.getTraitName())) {
                return traitValue;
            }
        }
        // finally, try the built-in traits
        for (TraitValue traitValue : builtInTraits.values()) {
            if (reference.equals(traitValue.getTraitName())) {
                return traitValue;
            }
        }

        return null;
    }

    @Override
    public String lookupEntitiesID(Object entity) {
        // Singletons should not be replaced, so we return null here, somewhat of
        // a hack
        return null;
    }

    // TASK -- Return all removable node, like locals, not just singletons
    public Collection<ASTNode> getRemovableLocalNodes() {
        // With multiple bindings in a realize statement the same ASTNode might
        // be roots for multiple things. So, we use a Set.
        Set<ASTNode> removable = new HashSet<ASTNode>();
        for (TraitValue traitValue : singletons.values()) {
            ASTNode astNode = traitValue.getSingleton().getRootNode();
            if (astNode != null) {
                removable.add(astNode);
            }
        }
        for (TraitValue traitValue : this.traits.values()) {
            TraitSignature traitType = traitValue.getTraitType();
            String traitName = traitType.getName();
            OptionInterface optionInterface = option.getOptionInterface();
            if (!optionInterface.declaresAsAbstract(traitName)
                && !traitType.isStaticDefinition())
            {
                for (EntityTuple entity : traitValue.getEntities()) {
                    ASTNode astNode = entity.getRootNode();
                    if (astNode != null) {
                        removable.add(astNode);
                    }
                }
            }

        }
        return removable;
    }

    public Option getOption() {
        return option;
    }

    public Collection<TraitValue> getSingletons() {
        return singletons.values();
    }

    public Collection<TraitValue> getInterfaceSingletons() {
        Set<String> interfaceSingletonNames = getInterfaceSingletonNames();
        ArrayList<TraitValue> result = Lists.newArrayList();
        String interfaceName = option.getOptionInterface().getName();
        for (String interfaceSingleton : interfaceSingletonNames) {
            if (interfaceSingleton.equals(interfaceName)) {
                // It still counts as an interface-level singleton, even though
                // in the requires map it uses the option's name instead. We need
                // to use this name in order for the lookup to work
                interfaceSingleton = option.getName();
            }
            TraitValue traitValue = singletons.get(interfaceSingleton);
            result.add(traitValue);
        }
        return result;
    }

    public Collection<TraitValue> getOptionSingletons() {
        Set<String> interfaceSingletonNames = getInterfaceSingletonNames();
        ArrayList<TraitValue> result = Lists.newArrayList();
        String optionName = option.getName();
        for (TraitValue traitValue : singletons.values()) {
            if (traitValue.getTraitName().equals(optionName))
                continue;
            if (!interfaceSingletonNames.contains(traitValue.getTraitName())) {
                result.add(traitValue);
            }
        }
        return result;
    }

    private Set<String> getInterfaceSingletonNames() {
        List<TraitSignature> tscs = option.getOptionInterface().getTraitSignatures();
        Set<String> result = Sets.newHashSet();
        for (TraitSignature type : tscs) {
            if (type.isSingleton()) {
                String name = type.getName();
                result.add(name);
            }
        }
        return result;
    }

    public Collection<TraitValue> getNonSingletons() {
        return traits.values();
    }

    // Returns the names of the singletons currently found in the table
//    public Set<String> getSingletonNames() {
//        Set<String> result = new HashSet<String>();
//        for (TraitValue singletonTupleSet : singletons.values()) {
//            result.addAll(singletonTupleSet.getNamesOfDeclaredFormals());
//        }
//        return result;
//    }

    @Override
    public FragmentParser newParser(boolean matchingMode) {
        FragmentParser parser = table.newParser(option);
        parser.setMatchingMode(matchingMode);
        return parser;
    }

    // Apply the require clause checks. Returns true if the checks pass.
    // (If false is returned then there is a user-error and processing should
    // stop.)
    public boolean checkExtraDefinitionConditions(EntityDataBase edb) {
        boolean result = true;

        Collection<TraitValue> singletonValues = singletons.values();
        for (TraitValue singletonValue : singletonValues) {
            TraitSignature type = singletonValue.getTraitType();
            EntityTuple tuple = singletonValue.getSingleton();
            result &= checkEntityTupleConditions(tuple, type, edb, this);
        }
        if (result == false)
            return false;

        Collection<TraitValue> traitValues = traits.values();
        for (TraitValue traitValue : traitValues) {
            TraitSignature type = traitValue.getTraitType();
            for (EntityTuple tuple : traitValue.getEntities()) {
                CurrentBindingsLookup lookup = new CurrentBindingsLookup(this, tuple);
                result &= checkEntityTupleConditions(tuple, type, edb, lookup);
            }
        }
        if (result == false)
            return false;

        FreeStandingRequirements requirements = option.getFreeStandingRequirements();
        checkFreeStandingConditions(edb, requirements);
        if (result == false)
            return false;
        requirements = option.getOptionInterface().getFreeStandingRequirements();
        checkFreeStandingConditions(edb, requirements);

        return result;
    }

    private boolean checkEntityTupleConditions(EntityTuple tuple, TraitSignature type,
        EntityDataBase edb, IEntityLookup lookup)
    {
        boolean result = true;

        List<ConstraintExpression> conditions = type.getRequireClauses();
        List<ErrorMessage> messages = type.getErrorMessages();
        RealizationStatement.checkPairConsistency(conditions, messages);

        if (DEBUG) {
            System.err.printf("Checking %s%n --against:%s%n", tuple, type);
        }

        for (int i = 0; i < conditions.size(); ++i) {
            ConstraintExpression condition = conditions.get(i);
            ErrorMessage message = messages.get(i);
            boolean value = condition.evaluate(lookup, edb, this);
            if (value == false) {
                String errorText;
                if (message == ErrorMessage.EMPTY_MESSAGE) {
                    errorText = String.format("Cannot satisfy constraint: %s", condition);
                }
                else {
                    errorText = message.getMessage(lookup);
                }
                ASTNode rootNode = tuple.getRootNode();
                SourceLocation astLocation;
                if (rootNode == null) {
                    if (message.hasLocation()) {
                        astLocation = message.getLocation(lookup, edb, this);
                    }
                    else {
                        astLocation = location;
                    }
                }
                else {
                    astLocation = new SourceLocation(rootNode);
                }
                ArcumError.userError(astLocation, errorText);
                result = false;
            }
        }
        return result;
    }

    private void checkFreeStandingConditions(EntityDataBase edb,
        FreeStandingRequirements requirements)
    {
        List<ConstraintExpression> conditions = requirements.getRequireClauses();
        List<ErrorMessage> messages = requirements.getErrorMessages();
        for (int i = 0; i < conditions.size(); ++i) {
            ConstraintExpression condition = conditions.get(i);
            ErrorMessage message = messages.get(i);
            boolean value = condition.evaluate(this, edb, this);
            if (value == false) {
                String errorText;
                if (message == ErrorMessage.EMPTY_MESSAGE) {
                    errorText = String.format("Cannot satisfy constraint: %s", condition);
                }
                else {
                    errorText = message.getMessage(this);
                }
                SourceLocation astLocation;
                if (message.hasLocation()) {
                    astLocation = message.getLocation(this, edb, this);
                }
                else {
                    astLocation = location;
                }
                ArcumError.userError(astLocation, errorText);
            }
        }
    }

    public SourceLocation getLocation() {
        return location;
    }

    // Extracts only the singleton or static trait entities in the form of a binding
    @Override
    public BindingMap extractAsBindings() {
        BindingMap result = BindingMap.newEmptyMap();
        for (TraitValue trait : singletons.values()) {
            EntityTuple singleton = trait.getSingleton();
            result.addBindings(new BindingMap(singleton.getValues()));
        }
        for (TraitValue trait : traits.values()) {
            if (trait.isStatic()) {
                result.bind(trait.getTraitName(), trait, EntityType.TRAIT);
            }
        }
        return result;
    }

    @Override
    public ITypeBinding lookupTypeBinding(ASTNode node) {
        EntityDataBase entityDataBase = table.getEntityDataBase();
        if (node instanceof AbstractTypeDeclaration) {
            AbstractTypeDeclaration atd = (AbstractTypeDeclaration)node;
            return entityDataBase.lookupTypeBinding(atd);
        }
        else if (node instanceof Expression) {
            Expression expr = (Expression)node;
            return expr.resolveTypeBinding();
        }
        else {
            ArcumError.fatalUserError(SourceLocation.resolveSourceLocation(),
                "There is no type associated with %s", Entity.getDisplayString(node));
            throw new Unreachable();
        }
    }

    @Override
    public AbstractTypeDeclaration lookupTypeDeclaration(ITypeBinding givenBinding) {
        return table.getEntityDataBase().lookupTypeDeclaration(givenBinding);
    }

    public Set<String> getTraitsRealized() {
        Set<String> result = Sets.newHashSet();
        result.addAll(builtInTraits.keySet());
        for (TraitValue singletonTupleSet : singletons.values()) {
            result.addAll(singletonTupleSet.getNamesOfDeclaredFormals());
        }
        return result;
    }

    @Override
    public TypeLookupTable getTypeLookupTable() {
        return new TypeLookupTable(getSingletons());
    }
}