package edu.ucsd.arcum.interpreter.fragments;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;

public enum ModifierElement implements ISynthesizedEntity {
    MOD_STATIC(Modifier.ModifierKeyword.STATIC_KEYWORD),
    MOD_ABSTRACT(Modifier.ModifierKeyword.ABSTRACT_KEYWORD),
    MOD_FINAL(Modifier.ModifierKeyword.FINAL_KEYWORD),
    MOD_NATIVE(Modifier.ModifierKeyword.NATIVE_KEYWORD),
    MOD_SYNCHRONIZED(Modifier.ModifierKeyword.SYNCHRONIZED_KEYWORD),
    MOD_TRANSIENT(Modifier.ModifierKeyword.TRANSIENT_KEYWORD),
    MOD_VOLATILE(Modifier.ModifierKeyword.VOLATILE_KEYWORD),
    MOD_STRICTFP(Modifier.ModifierKeyword.STRICTFP_KEYWORD),
    MOD_PUBLIC(Modifier.ModifierKeyword.PUBLIC_KEYWORD, true),
    MOD_PROTECTED(Modifier.ModifierKeyword.PROTECTED_KEYWORD, true),
    MOD_PRIVATE(Modifier.ModifierKeyword.PRIVATE_KEYWORD, true),
    MOD_PACKAGE(null, true)/*special default-access specifier*/,
    ;

    private ModifierKeyword keyword;
    private boolean isAccessSpecifier;

    private ModifierElement(ModifierKeyword keyword) {
        this.keyword = keyword;
        this.isAccessSpecifier = false;
    }

    private ModifierElement(ModifierKeyword keyword, boolean isAccessSpecifier) {
        this.keyword = keyword;
        this.isAccessSpecifier = isAccessSpecifier;
    }

    protected void buildString(StringBuilder buff, String indent) {
        if (keyword == null) {
            buff.append("package");
        }
        else {
            buff.append(keyword.toString());
        }
        buff.append(" (ModifierElement)");
    }

    public ModifierKeyword getKeyword() {
        return keyword;
    }

    public boolean isAccessSpecifier() {
        return isAccessSpecifier;
    }

    public boolean isSameModifier(Modifier modifier) {
        if (keyword == null)
            return false;
        return keyword.toString().equals(modifier.getKeyword().toString());
    }

    public static ModifierElement lookup(Modifier modifier) {
        for (ModifierElement element : values()) {
            if (element.isSameModifier(modifier))
                return element;
        }
        return null;
    }

    public Modifier asModifierASTNode(AST ast) {
        return ast.newModifier(this.keyword);
    }
}