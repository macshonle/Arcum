package edu.ucsd.arcum.interpreter.parser;

import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.*;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("restriction")
public class TokenData
{
    private static Map<Integer, String> tokenNames;

    static {
        tokenNames = new HashMap<Integer, String>();
        tokenNames.put(TokenNameWHITESPACE, "WHITESPACE");
        tokenNames.put(TokenNameCOMMENT_LINE, "COMMENT_LINE");
        tokenNames.put(TokenNameCOMMENT_BLOCK, "COMMENT_BLOCK");
        tokenNames.put(TokenNameCOMMENT_JAVADOC, "COMMENT_JAVADOC");
        tokenNames.put(TokenNameIdentifier, "Identifier");
        tokenNames.put(TokenNameabstract, "abstract");
        tokenNames.put(TokenNameassert, "assert");
        tokenNames.put(TokenNameboolean, "boolean");
        tokenNames.put(TokenNamebreak, "break");
        tokenNames.put(TokenNamebyte, "byte");
        tokenNames.put(TokenNamecase, "case");
        tokenNames.put(TokenNamecatch, "catch");
        tokenNames.put(TokenNamechar, "char");
        tokenNames.put(TokenNameclass, "class");
        tokenNames.put(TokenNamecontinue, "continue");
        tokenNames.put(TokenNameconst, "const");
        tokenNames.put(TokenNamedefault, "default");
        tokenNames.put(TokenNamedo, "do");
        tokenNames.put(TokenNamedouble, "double");
        tokenNames.put(TokenNameelse, "else");
        tokenNames.put(TokenNameenum, "enum");
        tokenNames.put(TokenNameextends, "extends");
        tokenNames.put(TokenNamefalse, "false");
        tokenNames.put(TokenNamefinal, "final");
        tokenNames.put(TokenNamefinally, "finally");
        tokenNames.put(TokenNamefloat, "float");
        tokenNames.put(TokenNamefor, "for");
        tokenNames.put(TokenNamegoto, "goto");
        tokenNames.put(TokenNameif, "if");
        tokenNames.put(TokenNameimplements, "implements");
        tokenNames.put(TokenNameimport, "import");
        tokenNames.put(TokenNameinstanceof, "instanceof");
        tokenNames.put(TokenNameint, "int");
        tokenNames.put(TokenNameinterface, "interface");
        tokenNames.put(TokenNamelong, "long");
        tokenNames.put(TokenNamenative, "native");
        tokenNames.put(TokenNamenew, "new");
        tokenNames.put(TokenNamenull, "null");
        tokenNames.put(TokenNamepackage, "package");
        tokenNames.put(TokenNameprivate, "private");
        tokenNames.put(TokenNameprotected, "protected");
        tokenNames.put(TokenNamepublic, "public");
        tokenNames.put(TokenNamereturn, "return");
        tokenNames.put(TokenNameshort, "short");
        tokenNames.put(TokenNamestatic, "static");
        tokenNames.put(TokenNamestrictfp, "strictfp");
        tokenNames.put(TokenNamesuper, "super");
        tokenNames.put(TokenNameswitch, "switch");
        tokenNames.put(TokenNamesynchronized, "synchronized");
        tokenNames.put(TokenNamethis, "this");
        tokenNames.put(TokenNamethrow, "throw");
        tokenNames.put(TokenNamethrows, "throws");
        tokenNames.put(TokenNametransient, "transient");
        tokenNames.put(TokenNametrue, "true");
        tokenNames.put(TokenNametry, "try");
        tokenNames.put(TokenNamevoid, "void");
        tokenNames.put(TokenNamevolatile, "volatile");
        tokenNames.put(TokenNamewhile, "while");
        tokenNames.put(TokenNameIntegerLiteral, "IntegerLiteral");
        tokenNames.put(TokenNameLongLiteral, "LongLiteral");
        tokenNames.put(TokenNameFloatingPointLiteral, "FloatingPointLiteral");
        tokenNames.put(TokenNameDoubleLiteral, "DoubleLiteral");
        tokenNames.put(TokenNameCharacterLiteral, "CharacterLiteral");
        tokenNames.put(TokenNameStringLiteral, "StringLiteral");
        tokenNames.put(TokenNamePLUS_PLUS, "++");
        tokenNames.put(TokenNameMINUS_MINUS, "--");
        tokenNames.put(TokenNameEQUAL_EQUAL, "==");
        tokenNames.put(TokenNameLESS_EQUAL, "<=");
        tokenNames.put(TokenNameGREATER_EQUAL, ">=");
        tokenNames.put(TokenNameNOT_EQUAL, "!=");
        tokenNames.put(TokenNameLEFT_SHIFT, "<<");
        tokenNames.put(TokenNameRIGHT_SHIFT, ">>");
        tokenNames.put(TokenNameUNSIGNED_RIGHT_SHIFT, ">>>");
        tokenNames.put(TokenNamePLUS_EQUAL, "+=");
        tokenNames.put(TokenNameMINUS_EQUAL, "-=");
        tokenNames.put(TokenNameMULTIPLY_EQUAL, "*=");
        tokenNames.put(TokenNameDIVIDE_EQUAL, "/=");
        tokenNames.put(TokenNameAND_EQUAL, "&=");
        tokenNames.put(TokenNameOR_EQUAL, "|=");
        tokenNames.put(TokenNameXOR_EQUAL, "^=");
        tokenNames.put(TokenNameREMAINDER_EQUAL, "%=");
        tokenNames.put(TokenNameLEFT_SHIFT_EQUAL, "<<=");
        tokenNames.put(TokenNameRIGHT_SHIFT_EQUAL, ">>=");
        tokenNames.put(TokenNameUNSIGNED_RIGHT_SHIFT_EQUAL, ">>>=");
        tokenNames.put(TokenNameOR_OR, "||");
        tokenNames.put(TokenNameAND_AND, "&&");
        tokenNames.put(TokenNamePLUS, "+");
        tokenNames.put(TokenNameMINUS, "-");
        tokenNames.put(TokenNameNOT, "!");
        tokenNames.put(TokenNameREMAINDER, "%");
        tokenNames.put(TokenNameXOR, "^");
        tokenNames.put(TokenNameAND, "&");
        tokenNames.put(TokenNameMULTIPLY, "*");
        tokenNames.put(TokenNameOR, "|");
        tokenNames.put(TokenNameTWIDDLE, "~");
        tokenNames.put(TokenNameDIVIDE, "/");
        tokenNames.put(TokenNameGREATER, ">");
        tokenNames.put(TokenNameLESS, "<");
        tokenNames.put(TokenNameLPAREN, "(");
        tokenNames.put(TokenNameRPAREN, ")");
        tokenNames.put(TokenNameLBRACE, "{");
        tokenNames.put(TokenNameRBRACE, "}");
        tokenNames.put(TokenNameLBRACKET, "[");
        tokenNames.put(TokenNameRBRACKET, "]");
        tokenNames.put(TokenNameSEMICOLON, ";");
        tokenNames.put(TokenNameQUESTION, "?");
        tokenNames.put(TokenNameCOLON, ":");
        tokenNames.put(TokenNameCOMMA, ",");
        tokenNames.put(TokenNameDOT, ".");
        tokenNames.put(TokenNameEQUAL, "=");
        tokenNames.put(TokenNameAT, "@");
        tokenNames.put(TokenNameELLIPSIS, "...");
        tokenNames.put(TokenNameEOF, "EOF");
        tokenNames.put(TokenNameERROR, "ERROR");
    }
    
    public static String tokenToString(int code) {
        String s = tokenNames.get(code);
        if (s == null) {
            return "Code: " + code;
        }
        else {
            return s;
        }
    }
}