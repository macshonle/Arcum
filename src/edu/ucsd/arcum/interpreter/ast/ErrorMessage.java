package edu.ucsd.arcum.interpreter.ast;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;
import edu.ucsd.arcum.interpreter.query.Entity;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.query.OptionMatchTable;
import edu.ucsd.arcum.interpreter.satisfier.Satisfier;

public class ErrorMessage
{
    public static ErrorMessage EMPTY_MESSAGE = new ErrorMessage();

    private SourceLocation position;
    private String messageString;
    private ConstraintExpression optLocationExpr;

    private ErrorMessage() {
        this(SourceLocation.GENERATED, "", null);
    }

    public ErrorMessage(SourceLocation position, String messageString, ConstraintExpression optLocationExpr) {
        this.position = position;
        this.messageString = messageString;
        this.optLocationExpr = optLocationExpr;
    }

    public String getMessage(IEntityLookup lookup)
    {
        List<String> textAndVars = lexTextAndVariableParts(messageString);
        StringBuilder builder = new StringBuilder();
        Iterator<String> it = textAndVars.iterator();
        builder.append(it.next());
        while (it.hasNext()) {
            String varName = it.next();
            Object entity = lookup.lookupEntity(varName);
            builder.append("\'");
            if (entity == null) {
                builder.append("##");
            }
            else {
                builder.append(Entity.getDisplayString(entity));
            }
            builder.append("\'");
            String messageText = it.next();
            builder.append(messageText);
        }
        return builder.toString();
    }

    // Takes in a message string and separates the components that represent error
    // message text from the components that represent unquoted variables. The first
    // and last element returned is guaranteed to be a message string. So, starting
    // at index 1, all odd numbered members are variables.
    //
    // E.x.: "Found `a and `b" -> {"Found ", "a", " and ", "b", ""}
    //       "`a is invalid" -> {"", "a", "is invalid"}
    private List<String> lexTextAndVariableParts(String string) {
        List<String> result = Lists.newArrayList();
        StringBuilder builder = new StringBuilder();
        for (int i=0; i<messageString.length(); ++i) {
            char c = messageString.charAt(i);
            if (c == '`') {
                String messageTextPart = builder.toString();
                result.add(messageTextPart);
                builder = new StringBuilder();
                ++i;
                while (i<messageString.length()
                    && Character.isJavaIdentifierPart(messageString.charAt(i))) {
                    builder.append(messageString.charAt(i));
                    ++i;
                }
                --i;
                String varName = builder.toString();
                if (varName.isEmpty()) {
                    ArcumError.fatalUserError(position, "A tick (`) must be followed"
                    		+ " by a valid variable name");
                }
                result.add(varName);
                builder = new StringBuilder();
            }
            else {
                builder.append(c);
            }
        }
        result.add(builder.toString());
        return result;
    }
    
    private List<String> getVariablesReferenced(String string) {
        List<String> list = lexTextAndVariableParts(string);
        List<String> result = Lists.newArrayList();
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            it.next(); // Eat the message text
            if (it.hasNext()) {
                result.add(it.next()); // Add the variable name
            }
        }
        return result;
    }

    private Object evaluateFunctionalExpression(IEntityLookup lookup,
        EntityDataBase edb, OptionMatchTable symTab, ConstraintExpression arg)
    {
        Satisfier sat = new Satisfier(arg);
        Object result = sat.evaluateEntityValue(lookup, edb, symTab);
        return result;
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("(");
        buff.append("\"");
        buff.append(messageString);
        buff.append("\"");
        if (optLocationExpr != null) {
            buff.append(", ");
            buff.append(optLocationExpr.toString());
        }
        buff.append(")");
        return buff.toString();
    }

    public boolean hasLocation() {
        return optLocationExpr != null;
    }

    public SourceLocation getLocation(IEntityLookup lookup, EntityDataBase edb,
        OptionMatchTable symTab)
    {
        Object entity = evaluateFunctionalExpression(lookup, edb, symTab, optLocationExpr);
        return SourceLocation.fromEntity(entity, edb);
    }

    public void checkUserDefinedPredicates(List<TraitSignature> tupleSets, Set<String> varsInScope) {
        List<String> varsReferenced = getVariablesReferenced(messageString);
        for (String var : varsReferenced) {
            if (!varsInScope.contains(var)) {
                ArcumError.fatalUserError(position,
                    "Reference to undefined variable %s (check spelling or scope)", var);
            }   
        }
        if (hasLocation()) {
            optLocationExpr.checkUserDefinedPredicates(tupleSets, varsInScope);
        }
    }
}