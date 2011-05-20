package edu.ucsd.arcum.interpreter.ast;

import java.util.HashMap;

public enum TraitModifier
{
    // An abstract tuple set is one specified at the interface level and realized
    // at the option level
    ABSTRACT("abstract"),

    // A defined tuple can contain zero or more instances, but cannot be translated
    // up to the interface level when it is option specific. A defined tuple can
    // also appear in an interface. A 'define' is good as a "helper expression."
    DEFINE("define"),

    ERROR("<ERROR>");

    private String keyword;
    private static HashMap<String, TraitModifier> lookup;
    static {
        lookup = new HashMap<String, TraitModifier>();
        for (TraitModifier tupleModifier : values()) {
            lookup.put(tupleModifier.getKeyword(), tupleModifier);
        }
    }

    private TraitModifier(String keyword) {
        this.keyword = keyword;
    }

    public String getKeyword() {
        return keyword;
    }

    public static TraitModifier lookup(String lexeme) {
        if (lookup.containsKey(lexeme)) {
            return lookup.get(lexeme);
        }
        else {
            return ERROR;
        }
    }
}
