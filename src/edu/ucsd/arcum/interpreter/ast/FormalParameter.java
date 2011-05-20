package edu.ucsd.arcum.interpreter.ast;

import static edu.ucsd.arcum.util.Accessor.getFunction;

import java.util.List;

import com.google.common.base.Function;

import edu.ucsd.arcum.interpreter.query.EntityType;

public class FormalParameter
{
    // EXAMPLE: Good example of auto-generated code: it drops out of any getter
    public static final Function<FormalParameter, String> getIdentifier = new Function<FormalParameter, String>() {
        @Override public String apply(FormalParameter formal) {
            return formal.getIdentifier();
        }
    };
    public static final Function<FormalParameter, String> getIdentifier2 = getFunction(FormalParameter.class,
        String.class, "getIdentifier");    

    public static final Function<FormalParameter, EntityType> getType = new Function<FormalParameter, EntityType>() {
        @Override public EntityType apply(FormalParameter formal) {
            return formal.getType();
        }
    };
    private EntityType type;
    private String identifier;
    private List<FormalParameter> traitArguments; // EXAMPLE: InternalField
    private Object defaultBody; // EXAMPLE: InternalField

    public FormalParameter(EntityType type, String identifier) {
        this.type = type;
        this.identifier = identifier;
    }

    @Override public String toString() {
        return type.toString() + " " + identifier;
    }

    @Override public boolean equals(Object other) {
        if (other == null || other.getClass() != this.getClass()) {
            return false;
        }
        else {
            FormalParameter param = (FormalParameter)other;
            return type.equals(param.type) && identifier.equals(param.identifier);
        }
    }

    @Override public int hashCode() {
        return type.hashCode() * 31 + identifier.hashCode();
    }

    public String getIdentifier() {
        return identifier;
    }

    public EntityType getType() {
        return type;
    }

    public static int findIndex(List<FormalParameter> params, String variable) {
        int len = params.size();
        for (int i = 0; i < len; ++i) {
            FormalParameter param = params.get(i);
            if (param.getIdentifier().equals(variable)) {
                return i;
            }
        }
        return -1;
    }

    public void addTraitArguments(List<FormalParameter> traitArguments) {
        this.traitArguments = traitArguments;
    }

    public boolean isSubTrait() {
        return traitArguments != null;
    }

    // EXAMPLE: For the future: be sure that this is called only in contexts where
    // isSubTrait has returned true
    public List<FormalParameter> getTraitArguments() {
        return traitArguments;
    }

    // Either a ConstraintExpression or a String
    public void addDefaultValue(Object defaultBody) {
        this.defaultBody = defaultBody;
    }

    public TraitSignature getSubTraitType() {
        TraitSignature result = TraitSignature.makeStaticDefinition(identifier,
            traitArguments.toArray(new FormalParameter[0]));
        return result;
    }
}
