package edu.ucsd.arcum.interpreter.query;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.HashMap;

// A source code entity is a program fragment; some participant in the concept
public enum EntityType
{
    ANY("<any_type>") {
        @Override public boolean isInstance(Object entity) {
            return true;
        }
    },

    ACCESS_SPECIFIER("AccessSpecifier"),

    ANNOTATION("Annotation"),

    BOOLEAN("Boolean"),

    DECLARATION_ELEMENT("DeclarationElement"),

    EXPR("Expr"),

    FIELD("Field"),

    METHOD("Method"),

    MODIFIERS("Modifiers"),

    PACKAGE("Package"),

    SIGNATURE("Signature"),

    STATEMENT("Statement"),

    STRING("String"),

    TYPE("Type"),

    // Special non-program fragment values
    TRAIT("<trait>"),

    ERROR("<ERROR>"),
    
    // GETDONE: Debugging some stuff, using this temporarily
    PUNT("<Whoops. You shouldn't see this.>"),

    ;

    private final String name;

    private static HashMap<String, EntityType> lookup;
    static {
        lookup = new HashMap<String, EntityType>();
        for (EntityType entityType : values()) {
            lookup.put(entityType.getName(), entityType);
        }
    }

    private EntityType(String name) {
        this.name = name;
    }

    @Override public String toString() {
        return getName();
    }

    public String getName() {
        return name;
    }

    public static EntityType lookup(String name) {
        if (lookup.containsKey(name)) {
            return lookup.get(name);
        }
        else {
            return ERROR;
        }
    }

    public boolean isAssignableFrom(EntityType entityType) {
        if (this == ANY) {
            return true;
        }

        if (this == entityType) {
            return true;
        }
        
        // if not a self match, try subtypes
        if (this == MODIFIERS) {
            return entityType == ACCESS_SPECIFIER;
        }
        else if (this == DECLARATION_ELEMENT) {
            return entityType == FIELD;
        }
        return false;
    }

    public boolean isInstance(Object entity) {
        EntityType type = EntityDataBase.getMostSpecificEntityType(entity);
        if (DEBUG) {
            System.out.printf("Entity %s is a %s%n", Entity.getDisplayString(entity), type);
        }
        return this.isAssignableFrom(type);
    }
}