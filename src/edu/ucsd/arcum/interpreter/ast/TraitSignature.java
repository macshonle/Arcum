package edu.ucsd.arcum.interpreter.ast;

import java.util.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;
import edu.ucsd.arcum.interpreter.ast.expressions.TrueLiteral;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.util.StringUtil;

public class TraitSignature extends ArcumDeclarationType
{
    private String name;
    private EnumSet<TraitModifier> modifiers;
    private List<FormalParameter> formals;
    private boolean isBuiltIn = false;
    private boolean isSingleton = false;
    private boolean isOptionLocal = false;
    private ConstraintExpression interfaceConjunct = new TrueLiteral(SourceLocation.GENERATED);

    private TraitSignature(List<TraitModifier> modifiers, String name,
        List<FormalParameter> formals)
    {
        this.modifiers = modifiers.isEmpty() ? EnumSet.noneOf(TraitModifier.class)
            : EnumSet.copyOf(modifiers);
        this.name = name;
        this.formals = formals;
        this.isBuiltIn = false;
    }

    public static TraitSignature makeTraitSignature(String name,
        List<FormalParameter> params)
    {
        List<TraitModifier> emptyList = Lists.newArrayList();
        return new TraitSignature(emptyList, name, params);
    }

    public static TraitSignature makeAbstractTraitSignature(String name,
        List<FormalParameter> params)
    {
        List<TraitModifier> modifiers = Lists.newArrayList(TraitModifier.ABSTRACT);
        return new TraitSignature(modifiers, name, params);
    }

    public static TraitSignature makeStaticDefinition(String name,
        List<FormalParameter> params)
    {
        return makeStaticDefinition(name, params.toArray(new FormalParameter[0]));
    }

    public static TraitSignature makeStaticDefinition(String name,
        FormalParameter... params)
    {
        List<TraitModifier> modifiers = Lists.newArrayList(TraitModifier.DEFINE);
        TraitSignature traitSignature;
        traitSignature = new TraitSignature(modifiers, name, Arrays.asList(params));
        traitSignature.isBuiltIn = true;
        return traitSignature;
    }

    public static TraitSignature makeBuiltIn(String name, FormalParameter... params) {
        List<TraitModifier> emptyList = Collections.emptyList();
        TraitSignature traitSignature;
        traitSignature = new TraitSignature(emptyList, name, Arrays.asList(params));
        traitSignature.isBuiltIn = true;
        return traitSignature;
    }

    public static TraitSignature makeSingleton(String name, FormalParameter... params) {
        List<TraitModifier> emptyList = Collections.emptyList();
        TraitSignature traitSignature;
        traitSignature = new TraitSignature(emptyList, name, Arrays.asList(params));
        traitSignature.isSingleton = true;
        return traitSignature;
    }

    public static TraitSignature makeSingleton(String name, List<FormalParameter> params)
    {
        return makeSingleton(name, params.toArray(new FormalParameter[0]));
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        for (TraitModifier mod : modifiers) {
            buff.append(mod.getKeyword());
            buff.append(" ");
        }
        enterTraitNames(buff);
        RealizationStatement.buildRequiresMessageString(buff, this);
        return buff.toString();
    }

    public String toSignatureOnlyString() {
        StringBuilder buff = new StringBuilder();
        enterTraitNames(buff);
        return buff.toString();
    }

    public void enterTraitNames(StringBuilder buff) {
        buff.append(name);
        buff.append("(");
        StringUtil.separate(buff, formals, ", ");
        buff.append(")");
    }

    public String getName() {
        return name;
    }

    public List<FormalParameter> getFormals() {
        return formals;
    }

    public int getNumberOfParameters() {
        return formals.size();
    }

    public List<TraitModifier> getModifiers() {
        return Lists.newArrayList(modifiers);
    }

    public boolean isStaticDefinition() {
        return modifiers.contains(TraitModifier.DEFINE);
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public boolean isSingleton() {
        return isSingleton;
    }

    public void setOptionLocal(boolean optionLocal) {
        this.isOptionLocal = optionLocal;
    }

    public boolean isOptionLocal() {
        return isOptionLocal;
    }

    public boolean isAbstract() {
        return modifiers.contains(TraitModifier.ABSTRACT);
    }

    public void setAbstract(boolean makeAbstract) {
        if (makeAbstract) {
            modifiers.add(TraitModifier.ABSTRACT);
        }
        else {
            modifiers.remove(TraitModifier.ABSTRACT);
        }
    }

    public EntityType lookupType(String id) {
        for (FormalParameter formal : formals) {
            if (formal.getIdentifier().equals(id)) {
                return formal.getType();
            }
        }
        ArcumError.fatalUserError(SourceLocation.coverAll(getRequireClauses()),
            "The variable '%s' has not been declared", id);
        return null;
    }

    public Collection<String> getNamesOfDeclaredFormals() {
        List<String> result = Lists.newArrayList();
        for (FormalParameter formal : formals) {
            result.add(formal.getIdentifier());
        }
        return result;
    }

    // Returns true if the names are the same and the formals are the same
    public boolean implementsSignature(TraitSignature interfaceSignature) {
        if (!name.equals(interfaceSignature.name))
            return false;
        if (formals.size() != interfaceSignature.formals.size())
            return false;
        for (int i = 0; i < formals.size(); ++i) {
            FormalParameter formal = formals.get(i);
            FormalParameter interfaceFormal = interfaceSignature.formals.get(i);
            if (!formal.equals(interfaceFormal))
                return false;
        }
        return true;
    }

    public void inheritConstraints(TraitSignature intfSignature) {
        List<ConstraintExpression> requireClauses = intfSignature.getRequireClauses();
        List<ErrorMessage> errorMessages = intfSignature.getErrorMessages();
        for (int i = 0; i < requireClauses.size(); ++i) {
            ConstraintExpression constraintExpression = requireClauses.get(i);
            ErrorMessage errorMessage = errorMessages.get(i);
            this.addRequiresClause(constraintExpression, errorMessage);
        }
    }

    public static Set<String> getGlobals(List<TraitSignature> signatures) {
        Set<String> result = new HashSet<String>();
        for (TraitSignature signature : signatures) {
            if (signature.isSingleton) {
                result.addAll(signature.getNamesOfDeclaredFormals());
            }
        }
        result.add(ArcumDeclarationTable.SPECIAL_ANY_VARIABLE);
        return result;
    }

    @Override protected Set<String> doGetVariablesInScope(Set<String> currentScope) {
        Set<String> result = Sets.newHashSet(currentScope);
        result.addAll(Lists.transform(formals, FormalParameter.getIdentifier));
        return result;
    }

    public ConstraintExpression getInterfaceConjunct() {
        return interfaceConjunct;
    }

    public void setInterfaceConjunct(ConstraintExpression conjunct) {
        this.interfaceConjunct = conjunct;
    }
}