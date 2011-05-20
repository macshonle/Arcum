package edu.ucsd.arcum.interpreter.ast;

import static edu.ucsd.arcum.interpreter.ast.ASTUtil.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.ASTUtil.NameAccessor;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;

public class Option extends TopLevelConstruct
{
    private final String optionInterfaceName;
    private final SourceLocation position;
    private final FreeStandingRequirements freeStandingRequirements;
    private OptionInterface optionInterface;
    private TraitSignature constructor;

    private Option(String name, String optionInterfaceName, String importsString,
        SourceLocation position)
    {
        super(name, importsString);
        this.optionInterfaceName = optionInterfaceName;
        this.optionInterface = null;
        this.constructor = null;
        this.position = position;
        this.freeStandingRequirements = new FreeStandingRequirements();
    }

    public static Option newOption(final String name, final String trait,
        final String importsString, ArcumDeclarationTable table,
        final SourceLocation position)
    {
        return table.conditionalCreate(name, new ConstructorThunk<Option>() {
            public Option create() {
                return new Option(name, trait, importsString, position);
            }
        });
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("option ");
        buff.append(getName());
        buff.append(" implements ");
        buff.append(optionInterfaceName);
        buff.append(String.format("%n{"));
        if (constructor != null) {
            buff.append(String.format("%n  "));
            buff.append(constructor.toString());
            buff.append(String.format("%n"));
        }
        for (RealizationStatement stmt : getRealizationStatements()) {
            buff.append(String.format("%n  "));
            buff.append(stmt.toString());
            buff.append(String.format(";%n"));
        }
        buff.append(String.format("}%n"));
        return buff.toString();
    }

    // non-mandatory constructor that an option can have
    // TODO: Support for this is probably not fully implemented
    public void addConstructor(TraitSignature constructor) {
        this.constructor = constructor;
    }

    @Override public void doTypeCheck(ArcumDeclarationTable table) {
        this.optionInterface = table.lookup(optionInterfaceName, OptionInterface.class);

        if (optionInterface == null) {
            ArcumError.fatalUserError(position, "The option %s's implementing"
                + " interface \"%s\" cannot be resolved:" + " possible typo", getName(),
                optionInterfaceName);
        }
        checkCorrectRealizations(optionInterface);
        checkSingletonNamesAreUnique();

        checkRealizationConsistency(optionInterface);
        List<TraitSignature> traitSignatures;
        traitSignatures = Lists.newArrayList(optionInterface.getTraitSignatures());
        List<RealizationStatement> stmts = getRealizationStatements();
        for (int i = 0; i < stmts.size(); ++i) {
            RealizationStatement stmt = stmts.get(i);
            List<TraitSignature> tuplesRealized = stmt.getTuplesRealized();
            if (tuplesRealized.size() == 1) {
                TraitSignature traitSignature = tuplesRealized.get(0);
                String traitName = traitSignature.getName();
                if (!optionInterface.declaresAsAbstract(traitName) && !stmt.isStatic()) {
                    stmts.set(i, StaticRealizationStatement.makeLocal(stmt,
                        traitSignature));
                }
            }
        }
        for (RealizationStatement stmt : stmts) {
            List<TraitSignature> tuplesRealized = stmt.getTuplesRealized();
            traitSignatures.addAll(tuplesRealized);
        }
        for (RealizationStatement stmt : getRealizationStatements()) {
            checkExpressionPredicates(stmt, traitSignatures);
        }
        checkRequireClausePredicates(traitSignatures);
        Set<String> varsInScope = TraitSignature.getGlobals(traitSignatures);
        freeStandingRequirements.checkUserDefinedPredicates(traitSignatures, varsInScope);
        // TODO: also type check the program fragment types, argument types, etc

        //  * the constructor singleton cannot be realized or defined
    }

    // Check that each abstract tuple set in the trait is realized exactly once
    private void checkCorrectRealizations(OptionInterface optionInterface) {
        List<String> allTuplesRealized = new ArrayList<String>();
        for (RealizationStatement stmt : getRealizationStatements()) {
            List<TraitSignature> tuplesRealized = stmt.getTuplesRealized();
            extractNames(allTuplesRealized, tuplesRealized,
                new NameAccessor<TraitSignature>() {
                    public String getName(TraitSignature element) {
                        return element.getName();
                    }
                });
        }
        checkNames(allTuplesRealized, IDENTITY_ACCESSOR, "The tuple set %s cannot"
            + " be realized more than once");
        List<TraitSignature> traitSignatures = optionInterface.getTraitSignatures();
        List<String> traitsToRealize = new ArrayList<String>();
        for (TraitSignature tupleSet : traitSignatures) {
            if (tupleSet.isAbstract()) {
                String traitName = tupleSet.getName();
                traitsToRealize.add(traitName);
            }
        }
        for (String toRealize : traitsToRealize) {
            if (!allTuplesRealized.contains(toRealize)) {
                ArcumError.fatalUserError(position,
                    "The option %s must realize the \'%s\' tuple set", this.getName(),
                    toRealize);
            }
        }
    }

    // each name introduced by a singleton is unique
    private void checkSingletonNamesAreUnique() {
        List<String> singletonElements = new ArrayList<String>();
        for (RealizationStatement stmt : getRealizationStatements()) {
            List<TraitSignature> tuplesRealized = stmt.getTuplesRealized();
            for (TraitSignature tupleRealized : tuplesRealized) {
                if (tupleRealized.isSingleton()) {
                    singletonElements.add(tupleRealized.getName());
                }
            }
        }
        checkNames(singletonElements, IDENTITY_ACCESSOR, "Name clash: %s");
    }

    // assumes a doTypeCheck has already been performed
    public List<FormalParameter> getSingletonParameters(ArcumDeclarationTable table) {
        OptionInterface optionInterface;
        optionInterface = table.lookup(optionInterfaceName, OptionInterface.class);
        List<FormalParameter> result = optionInterface.getSingletonParameters();
        if (constructor != null) {
            List<FormalParameter> optionFormals = constructor.getFormals();
            List<String> allNames = new ArrayList<String>();
            extractNames(allNames, optionFormals, PARAMETER_NAME);
            extractNames(allNames, result, PARAMETER_NAME);
            checkNames(allNames, IDENTITY_ACCESSOR);
            result.addAll(optionFormals);
        }
        return result;
    }

    public RealizationStatement getStatementForReplacement(String name) {
        for (RealizationStatement stmt : getRealizationStatements()) {
            List<TraitSignature> tuplesRealized = stmt.getTuplesRealized();
            if (tuplesRealized.size() == 1) {
                TraitSignature signature = tuplesRealized.get(0);
                if (signature.getName().equals(name)) {
                    return stmt;
                }
            }
            else if (tuplesRealized.size() != 0) {
                boolean allSingletons = true;
                for (TraitSignature tupleRealized : tuplesRealized) {
                    if (!tupleRealized.isSingleton()) {
                        allSingletons = false;
                        break;
                    }
                }
                if (!allSingletons) {
                    ArcumError
                        .fatalError("Cannot realize more than one abstract trait at a time");
                }
            }
        }
        return null;
    }

    // TASK -- All locals, not just singletons, and the static defines they may
    // depend upon
    // TASK -- Ultimately the concept of singleton versus local needs to be removed
    // completely. In terms of language syntax and implementation.
    public List<RealizationStatement> getRealizationsOfLocals() {
        List<RealizationStatement> result = new ArrayList<RealizationStatement>();
        List<RealizationStatement> stmts = getRealizationStatements();
        for (RealizationStatement stmt : stmts) {
            if (stmt.isSingletonRealization() || stmt.isLocal() || stmt.isStatic()) {
                result.add(stmt);
            }
        }
        return result;
    }

    public OptionInterface getOptionInterface() {
        return optionInterface;
    }

    public FreeStandingRequirements getFreeStandingRequirements() {
        return freeStandingRequirements;
    }
}