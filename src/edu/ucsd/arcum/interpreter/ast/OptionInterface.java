package edu.ucsd.arcum.interpreter.ast;

import static edu.ucsd.arcum.interpreter.ast.ASTUtil.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;

public class OptionInterface extends TopLevelConstruct
{
    private final SourceLocation location;
    private final List<TraitSignature> traitSignatures;
    private final List<TraitSignature> subTraitConstraints;
    private final FreeStandingRequirements freeStandingRequirements;

    private OptionInterface(SourceLocation location, String name, String importsString) {
        super(name, importsString);
        this.location = location;
        this.traitSignatures = Lists.newArrayList();
        this.subTraitConstraints = Lists.newArrayList();
        this.freeStandingRequirements = new FreeStandingRequirements();
    }

    public static OptionInterface newOptionInterface(final SourceLocation location,
        final String name, final String importsString, ArcumDeclarationTable table)
    {
        return table.conditionalCreate(name, new ConstructorThunk<OptionInterface>() {
            public OptionInterface create() {
                return new OptionInterface(location, name, importsString);
            }
        });
    }

    public void addTupleSetType(TraitSignature set) {
        traitSignatures.add(set);
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("interface ");
        buff.append(getName());
        buff.append("\n{");
        for (TraitSignature tupleSet : traitSignatures) {
            buff.append("\n  ");
            buff.append(tupleSet.toString());
            buff.append("\n");
        }
        buff.append("}\n");
        return buff.toString();
    }

    @Override public void doTypeCheck(ArcumDeclarationTable table) {
        traitSignatures.addAll(EntityDataBase.BUILT_IN_TRAIT_TYPES.values());
        TraitSignature constructor = findConstructor();
        for (FormalParameter formal : constructor.getFormals()) {
            if (formal.isSubTrait()) {
                TraitSignature subTrait = formal.getSubTraitType();
                traitSignatures.add(subTrait);
                subTraitConstraints.add(subTrait);
            }
        }

        NameAccessor<TraitSignature> tupleSetNameExtractor;
        tupleSetNameExtractor = new NameAccessor<TraitSignature>() {
            public String getName(TraitSignature tupleSet) {
                return tupleSet.getName();
            }
        };
        checkRealizationConsistency(this);
        for (RealizationStatement stmt : getRealizationStatements()) {
            if (stmt.isStatic()) {
                List<TraitSignature> tuplesRealized = stmt.getTuplesRealized();
                traitSignatures.addAll(tuplesRealized);
            }
        }
        checkNames(traitSignatures, tupleSetNameExtractor);
        for (TraitSignature tupleSet : traitSignatures) {
            checkFormals(tupleSet.getFormals());
            checkModifiers(tupleSet.getModifiers());
        }
        List<String> allNames = new ArrayList<String>();
        extractNames(allNames, traitSignatures, tupleSetNameExtractor);
        checkNames(allNames, IDENTITY_ACCESSOR);
        for (RealizationStatement stmt : getRealizationStatements()) {
            checkExpressionPredicates(stmt, traitSignatures);
        }
        checkRequireClausePredicates(traitSignatures);
        Set<String> varsInScope = TraitSignature.getGlobals(traitSignatures);
        freeStandingRequirements.checkUserDefinedPredicates(traitSignatures, varsInScope);
    }

    private void checkModifiers(List<TraitModifier> modifiers) {
        if (modifiers.indexOf(TraitModifier.ABSTRACT) != modifiers
            .lastIndexOf(TraitModifier.ABSTRACT))
        {
            ArcumError.fatalError("Keyword abstract present multiple times!");
        }
        if (modifiers.indexOf(TraitModifier.ERROR) != -1) {
            ArcumError.fatalError("Strange modifier error");
        }
    }

    // assumes a doTypeCheck has already been performed
    public List<FormalParameter> getSingletonParameters() {
        TraitSignature constructor = findConstructor();
        if (constructor == null) {
            // parameters list can be empty
            return new ArrayList<FormalParameter>();
        }
        if (!constructor.isSingleton()) {
            ArcumError.fatalError("The constructor tuple set must be"
                + " declared as a singleton");
        }
        return constructor.getFormals();
    }

    private TraitSignature findConstructor() {
        for (TraitSignature tupleSet : traitSignatures) {
            if (tupleSet.getName().equals(this.getName())) {
                return tupleSet;
            }
        }
        ArcumError.fatalUserError(location, "A constructor named %s must be present",
            getName());
        return null;
    }

    public List<TraitSignature> getTraitSignatures() {
        return traitSignatures;
    }

    public List<TraitSignature> getSubTraitConstraints() {
        return subTraitConstraints;
    }

    public TraitSignature lookupTupleSet(String name) {
        for (TraitSignature tupleSet : traitSignatures) {
            if (tupleSet.getName().equals(name)) {
                return tupleSet;
            }
        }
        return null;
    }

    public boolean hasSingletonMember(String name) {
        TraitSignature type = lookupTupleSet(name);
        if (type != null && type.isSingleton()) {
            return true;
        }
        else {
            return false;
        }
    }

    public SourceLocation getLocation() {
        return location;
    }

    public boolean declaresAsAbstract(String traitName) {
        for (TraitSignature tupleSet : traitSignatures) {
            if (tupleSet.getName().equals(traitName)) {
                return tupleSet.isAbstract();
            }
        }
        return false;
    }

    public TraitSignature getTraitSignature(String traitName) {
        for (TraitSignature tupleSet : traitSignatures) {
            if (tupleSet.getName().equals(traitName)) {
                return tupleSet;
            }
        }
        return null;
    }

    public FreeStandingRequirements getFreeStandingRequirements() {
        return freeStandingRequirements;
    }
}
