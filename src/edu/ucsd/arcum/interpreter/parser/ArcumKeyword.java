package edu.ucsd.arcum.interpreter.parser;

import java.util.HashMap;

public enum ArcumKeyword
{
    DEFINE_KEYWORD("define"),
    EXISTS_KEYWORD("exists"),
    FORALL_KEYWORD("forall"),
    ONFAIL_KEYWORD("onfail"),
    OPTION_KEYWORD("option"),
    REALIZE_KEYWORD("realize"),
    REQUIRE_KEYWORD("require"),
    SELECT_KEYWORD("select"),
    ERROR("<ERROR>");

    private String lexeme;

    ArcumKeyword(String lexeme) {
        this.lexeme = lexeme;
    }

    public String getLexeme() {
        return lexeme;
    }

    @Override public String toString() {
        return lexeme;
    }

    private static HashMap<String, ArcumKeyword> lookup;
    static {
        lookup = new HashMap<String, ArcumKeyword>();
        for (ArcumKeyword keyword : values()) {
            lookup.put(keyword.getLexeme(), keyword);
        }
    }

    public static ArcumKeyword lookup(String lexeme) {
        if (lookup.containsKey(lexeme)) {
            return lookup.get(lexeme);
        }
        else {
            return ERROR;
        }
    }
}