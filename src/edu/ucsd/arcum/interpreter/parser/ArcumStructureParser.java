package edu.ucsd.arcum.interpreter.parser;

import static edu.ucsd.arcum.interpreter.parser.ArcumKeyword.*;
import static edu.ucsd.arcum.interpreter.parser.BacktrackingScanner.TokenNameARCUMBEGINQUOTE;
import static edu.ucsd.arcum.interpreter.parser.TokenData.tokenToString;
import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.*;
import edu.ucsd.arcum.interpreter.ast.expressions.*;
import edu.ucsd.arcum.interpreter.parser.BacktrackingScanner.TokenCountListener;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.query.EntityType;

// Used in the parsing of an .arcum source file
@SuppressWarnings("restriction")
public class ArcumStructureParser
{
    private enum ParseMode
    {
        // Accept only valid function arguments: Variable references, implied exists,
        // and immediate-literal patterns
        FUNCTION_ARGUMENTS,

        // Any constraint expression is allowed
        CONSTRAINT_EXPR,

        // Anything on the right-hand side of a unification: Constants,
        // variable references, and all patterns (immediate-literal and matching),
        // a select expression, or a disjunct of the same
        VALUES_ONLY
    }

    private final IResource resource;
    private final IProject project;
    private final String importsString;
    private int numErrors;

    public ArcumStructureParser(IResource resource, IProject project, String importsString)
    {
        this.resource = resource;
        this.project = project;
        this.importsString = importsString;
        this.numErrors = 0;
    }

    public void parse(BacktrackingScanner scanner, ArcumDeclarationTable table) {
        // e.g.,
        // option InternalField { ...
        // option interface AttributeConcept { ...
        if (arcumKeyword(OPTION_KEYWORD, scanner)) {
            match(scanner);
            if (scanner.lookaheadEquals(TokenNameinterface)) {
                match(TokenNameinterface, scanner);
                parseOptionInterface(table, importsString, scanner);
            }
            else {
                parseOption(table, importsString, scanner);
            }
        }
        // interface AttributeConcept { ...
        else if (scanner.lookaheadEquals(TokenNameinterface)) {
            match(scanner);
            parseOptionInterface(table, importsString, scanner);
        }
        // require { Option1(); Option2(); ... }
        else if (arcumKeyword(REQUIRE_KEYWORD, scanner)) {
            match(scanner);
            parseTopLevelRequire(table, importsString, project, scanner);
        }
        else {
            SourceLocation location = scanner.getCurrentLocation();
            ++numErrors;
            ArcumError
                .fatalUserError(location, "Expected start of option,"
                    + " option interface, or requires: %s%n", scanner
                    .getCurrentTokenString());
        }
    }

    private void parseOptionInterface(ArcumDeclarationTable table, String importsString,
        BacktrackingScanner scanner)
    {
        String name = scanner.getCurrentTokenString();
        SourceLocation location = scanner.getCurrentLocation();
        match(TokenNameIdentifier, scanner);
        OptionInterface optionInterface = OptionInterface.newOptionInterface(location,
            name, importsString, table);
        match(TokenNameLBRACE, scanner);
        parseOptionInterfaceMembers(optionInterface, scanner);
        match(TokenNameRBRACE, scanner);
    }

    // e.g.:
    // abstract attrGet(Expr root, Expr targetExpr) { require: ...  }
    // abstract AccessSpecifier spec;
    // define classGraph(Type toType, Type fromType, Field edge) { ... }
    private void parseOptionInterfaceMembers(OptionInterface optionInterface,
        BacktrackingScanner scanner)
    {
        int ctorsFound = 0;
        while (scanner.lookaheadIsNoneOf(TokenNameRBRACE, TokenNameEOF)) {
            if (arcumKeyword(DEFINE_KEYWORD, scanner)) {
                RealizationStatement statement = parseRealizationStatement(
                    optionInterface, scanner);
                List<TraitSignature> tuplesRealized = statement.getTuplesRealized();
                if (tuplesRealized.size() != 1) {
                    ArcumError.fatalError("Internal error found in"
                        + " parseOptionInterfaceMembers");
                }
                TraitSignature signature = tuplesRealized.get(0);
            }
            else if (scanner.lookaheadEquals(TokenNameabstract)) {
                match(TokenNameabstract, scanner);
                String typeOrTraitName = scanner.getCurrentTokenString();
                SourceLocation startLocation = scanner.getCurrentLocation();
                match(TokenNameIdentifier, scanner);
                TraitSignature signature;
                if (scanner.lookaheadEquals(TokenNameLPAREN)) {
                    String traitName = typeOrTraitName;
                    List<FormalParameter> formals = parseParameterList(scanner, false);
                    signature = TraitSignature.makeAbstractTraitSignature(traitName,
                        formals);
                }
                else if (scanner.lookaheadEquals(TokenNameIdentifier)) {
                    String typeName = typeOrTraitName;
                    String varName = scanner.getCurrentTokenString();
                    match(TokenNameIdentifier, scanner);
                    EntityType type = getEntityType(typeName, startLocation);
                    FormalParameter formal = new FormalParameter(type, varName);
                    signature = TraitSignature.makeSingleton(varName, formal);
                }
                else {
                    ArcumError.fatalUserError(scanner.getCurrentLocation(),
                        "Expected either a \'(\' or an identifier");
                    return;
                }
                if (scanner.lookaheadEquals(TokenNameSEMICOLON)) {
                    match(TokenNameSEMICOLON, scanner);
                }
                else {
                    match(TokenNameLBRACE, scanner);
                    if (!scanner.lookaheadEquals(TokenNameRBRACE)
                        && !arcumKeyword(REQUIRE_KEYWORD, scanner)) {
                        ConstraintExpression expr = parseConstraintExpr(scanner);
                        signature.setInterfaceConjunct(expr);
                    }
                    parseOptionalRequireClauses(scanner, signature);
                    match(TokenNameRBRACE, scanner);
                }
                optionInterface.addTupleSetType(signature);
            }
            else if (isFreeStandingRequirementStart(scanner)) {
                FreeStandingRequirements requirements;
                requirements = optionInterface.getFreeStandingRequirements();
                parseFreeStandingRequirements(scanner, requirements);
            }
            else if (scanner.lookaheadEquals(TokenNameIdentifier)) {
                String ctorName = scanner.getCurrentTokenString();
                SourceLocation location = scanner.getCurrentLocation();
                match(TokenNameIdentifier, scanner);
                if (!ctorName.equals(optionInterface.getName())) {
                    ++numErrors;
                    ArcumError.fatalUserError(location,
                        "The constructor must be named %s (expected constructor,"
                            + " or a clause beginning with \'abstract\' or \'define\')",
                        optionInterface.getName());
                }

                if (ctorsFound > 1) {
                    ++numErrors;
                    ArcumError.fatalUserError(scanner.getCurrentLocation(),
                        "A constructor for this interface already exists");
                }
                ++ctorsFound;

                TraitSignature tupleSet = parseConstructor(ctorName, scanner);
                parseOptionalRequireClauses(scanner, tupleSet);
                optionInterface.addTupleSetType(tupleSet);
            }
            else {
                ++numErrors;
                ArcumError.fatalUserError(scanner.getCurrentLocation(),
                    "Unexpected \'%s\', expected \'abstract\' or \'define\' clause",
                    scanner.getCurrentTokenString());
            }
        }
        if (ctorsFound < 1) {
            ++numErrors;
            ArcumError.fatalUserError(optionInterface.getLocation(),
                "A constructor named %s must be defined", optionInterface.getName());
        }
    }

    private ErrorMessage parseOptionalOnFailMessage(BacktrackingScanner scanner) {
        ErrorMessage result = null;
        if (arcumKeyword(ArcumKeyword.ONFAIL_KEYWORD, scanner)) {
            matchKeyword(ArcumKeyword.ONFAIL_KEYWORD, scanner);
            match(TokenNameLBRACE, scanner);
            result = parseErrorMessageText(scanner);
            match(TokenNameRBRACE, scanner);
        }
        return result;
    }

    private boolean isErrorMessageTextStart(BacktrackingScanner scanner) {
        return scanner.lookaheadEquals(TokenNameStringLiteral);
    }

    // An error message in the form of a series of concatenated string literals. An
    // optional argument is the program element to which the error message applies.
    private ErrorMessage parseErrorMessageText(BacktrackingScanner scanner) {
        StringBuilder buff = new StringBuilder();
        SourceLocation messageLocation = scanner.getCurrentLocation();
        if (scanner.lookaheadIsNoneOf(TokenNameStringLiteral)) {
            ++numErrors;
            ArcumError.fatalUserError(scanner.getCurrentLocation(),
                "Expected a string literal here");
        }
        String messagePart = scanner.getCurrentStringLiteral();
        match(TokenNameStringLiteral, scanner);
        buff.append(messagePart);
        while (scanner.lookaheadEquals(TokenNamePLUS)) {
            match(scanner);
            messagePart = scanner.getCurrentStringLiteral();
            match(TokenNameStringLiteral, scanner);
            buff.append(messagePart);
        }
        String message = buff.toString();

        ConstraintExpression optLocationExpr = null;
        if (scanner.lookaheadEquals(TokenNameCOMMA)) {
            match(scanner);
            optLocationExpr = parseBooleanFactor(scanner, ParseMode.FUNCTION_ARGUMENTS);
        }
        return new ErrorMessage(messageLocation, message, optLocationExpr);
    }

    // parses a constructor
    private TraitSignature parseConstructor(String name, BacktrackingScanner scanner) {
        List<FormalParameter> params = parseParameterList(scanner, true);
        TraitSignature traitSignature = TraitSignature.makeSingleton(name, params);
        if (scanner.lookaheadEquals(TokenNameLBRACE)) {
            match(TokenNameLBRACE, scanner);
            parseOptionalRequireClauses(scanner, traitSignature);
            match(TokenNameRBRACE, scanner);
        }
        else {
            match(TokenNameSEMICOLON, scanner);
        }
        return traitSignature;
    }

    // expr ::= term
    // expr ::= expr || term
    private ConstraintExpression parseConstraintExpr(BacktrackingScanner scanner) {
        ConstraintExpression term = parseBooleanTerm(scanner);
        Parsing: for (;;) {
            switch (scanner.lookahead()) {
            case TokenNameOR_OR:
            {
                match(scanner);
                ConstraintExpression operand = parseBooleanTerm(scanner);
                term = addNewOperand(term, BooleanDisjunction.class, operand);
                break;
            }
            default:
                break Parsing;
            }
        }
        return term;
    }

    // term ::= factor
    // term ::= term && factor
    private ConstraintExpression parseBooleanTerm(BacktrackingScanner scanner) {
        ConstraintExpression factor = parseBooleanFactor(scanner,
            ParseMode.CONSTRAINT_EXPR);
        Parsing: for (;;) {
            switch (scanner.lookahead()) {
            case TokenNameAND_AND:
            {
                match(scanner);
                ConstraintExpression operand;
                operand = parseBooleanFactor(scanner, ParseMode.CONSTRAINT_EXPR);
                factor = addNewOperand(factor, BooleanConjunction.class, operand);
                break;
            }
            default:
                break Parsing;
            }
        }
        return factor;
    }

    private <T extends VariadicOperator> ConstraintExpression addNewOperand(
        ConstraintExpression term, Class<T> clazz, ConstraintExpression condition)
    {
        T result = null;
        if (clazz.isAssignableFrom(term.getClass())) {
            result = clazz.cast(term);
        }
        else {
            try {
                Constructor<T> ctor = clazz.getConstructor(SourceLocation.class);
                result = ctor.newInstance(term.getPosition());
                result.addClause(term);
            }
            catch (RuntimeException e) {
                throw e;
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        result.addClause(condition);
        return result;
    }

    // factor ::= ( expr [<=> expr]* ) [zero or more]*
    // factor ::= functionName(exprs,..)
    // factor ::= exists (..) {..}
    // factor ::= forall (..) {..}
    // factor ::= select { .. }
    // factor ::= true
    // factor ::= false
    // factor ::= <string-literal>
    // factor ::= [ pattern ]
    // factor ::= < immediate-pattern >
    private ConstraintExpression parseBooleanFactor(BacktrackingScanner scanner,
        ParseMode mode)
    {
        ConstraintExpression result;
        boolean argOK = false;
        boolean valueOK = false;
        int theToken = scanner.lookahead();
        switch (theToken) {
        case TokenNameLPAREN:
            match(TokenNameLPAREN, scanner);
            result = parseConstraintExpr(scanner);
            // the <=> operator
            while (scanner.lookaheadEquals(TokenNameLESS_EQUAL)) {
                BooleanEquivalence boolEquiv;
                if (result instanceof BooleanEquivalence) {
                    boolEquiv = (BooleanEquivalence)result;
                }
                else {
                    boolEquiv = new BooleanEquivalence(result.getPosition());
                    boolEquiv.addClause(result);
                    result = boolEquiv;
                }
                int pos = scanner.getCurrentTokenStartPosition();
                match(TokenNameLESS_EQUAL, scanner);
                if (scanner.lookaheadIsNoneOf(TokenNameGREATER)
                    || scanner.getCurrentTokenStartPosition() != (pos + 2))
                {
                    ++numErrors;
                    ArcumError.fatalUserError(scanner.getCurrentLocation(),
                        "Insert '>' symbol to complete '<=>' operator");
                }
                match(TokenNameGREATER, scanner);
                ConstraintExpression nextTerm = parseConstraintExpr(scanner);
                boolEquiv.addClause(nextTerm);
            }
            match(TokenNameRPAREN, scanner);
            break;
        case TokenNameIdentifier:
            if (arcumKeyword(EXISTS_KEYWORD, scanner)) {
                result = parseExistsExpression(scanner);
            }
            else if (arcumKeyword(FORALL_KEYWORD, scanner)) {
                result = parseForallExpression(scanner);
            }
            else if (arcumKeyword(SELECT_KEYWORD, scanner)) {
                result = parseSelectExpression(scanner);
                valueOK = true;
            }
            else {
                String id = scanner.getCurrentTokenString();
                SourceLocation start = scanner.getCurrentLocation();
                match(TokenNameIdentifier, scanner);
                // id(args) /*functional expr*/
                if (scanner.lookaheadEquals(TokenNameLPAREN)) {
                    FunctionalExpression function;
                    function = parseBooleanFunction(start, id, scanner);
                    if (mode == ParseMode.FUNCTION_ARGUMENTS
                        || mode == ParseMode.VALUES_ONLY)
                    {
                        // TODO: FRIDAY: Change the parser here!
//                        if (!function.isAccessor()) {
//                            ++numErrors;
//                            fatalUserError(function.getPosition(),
//                                "Only accessor functions are allowed in this context, not predicates");
//                        }
                    }
                    result = function;
                    argOK = true;
                    valueOK = true;
                }
                // id == factor /*unify*/
                else if (scanner.lookaheadEquals(TokenNameEQUAL_EQUAL)) {
                    match(TokenNameEQUAL_EQUAL, scanner);
                    ConstraintExpression rhs;
                    if (scanner.lookaheadEquals(TokenNameLPAREN)) {
                        BooleanDisjunction union;
                        union = new BooleanDisjunction(scanner.getCurrentLocation());
                        match(TokenNameLPAREN, scanner);
                        for (;;) {
                            ConstraintExpression disjunct;
                            disjunct = parseBooleanFactor(scanner, ParseMode.VALUES_ONLY);
                            union.addClause(disjunct);
                            if (scanner.lookaheadEquals(TokenNameOR_OR)) {
                                match(TokenNameOR_OR, scanner);
                            }
                            else {
                                break;
                            }
                        }
                        match(TokenNameRPAREN, scanner);
                        rhs = union;
                    }
                    else {
                        rhs = parseBooleanFactor(scanner, ParseMode.VALUES_ONLY);
                    }
                    SourceLocation extent = start.extendedTo(rhs.getPosition());
                    result = new UnificationExpression(extent, id, rhs);
                }
                // id /*just an id*/
                else {
                    result = new VariableReferenceExpression(start, id);
                    argOK = true;
                    valueOK = true;
                }
            }
            break;
        case TokenNametrue:
            result = new TrueLiteral(scanner.getCurrentLocation());
            match(scanner);
            valueOK = true;
            break;
        case TokenNamefalse:
            result = new FalseLiteral(scanner.getCurrentLocation());
            match(scanner);
            valueOK = true;
            break;
        case TokenNameStringLiteral:
        {
            String text = scanner.getCurrentTokenString();
            result = new StringLiteral(scanner.getCurrentLocation(), text);
            match(scanner);
            valueOK = true;
            break;
        }
        case TokenNameLESS:
        {
            PatternExpression patExpr;
            patExpr = parsePatternExpression(scanner, TokenNameLESS, TokenNameGREATER);
            patExpr.setImmediate(true);
            result = patExpr;
            argOK = true;
            valueOK = true;
            break;
        }
        case TokenNameLBRACKET:
            result = parsePatternExpression(scanner, TokenNameLBRACKET, TokenNameRBRACKET);
            valueOK = true;
            break;
        case TokenNameNOT:
        {
            SourceLocation start = scanner.getCurrentLocation();
            match(scanner);
            ConstraintExpression factor = parseBooleanFactor(scanner,
                ParseMode.CONSTRAINT_EXPR);
            SourceLocation location = start.extendedTo(factor.getPosition());
            result = new BooleanNegation(location, factor);
            break;
        }
        default:
            SourceLocation location = scanner.getCurrentLocation();
            ++numErrors;
            ArcumError.fatalUserError(location, "Unexpected \"%s\"",
                tokenToString(theToken));
            return null /*unreachable*/;
        }

        if (mode == ParseMode.FUNCTION_ARGUMENTS && !argOK) {
            ++numErrors;
            ArcumError.fatalUserError(result.getPosition(),
                "Construct not allowed for function arguments");
        }
        if (mode == ParseMode.VALUES_ONLY && !valueOK) {
            ++numErrors;
            ArcumError.fatalUserError(result.getPosition(),
                "Unification can only be on values or union of values");
        }
        return result;
    }

    // exists (Method m) { acceptMethods(m) && within(root, m) }
    private ConstraintExpression parseExistsExpression(BacktrackingScanner scanner) {
        SourceLocation start = scanner.getCurrentLocation();
        matchKeyword(EXISTS_KEYWORD, scanner);
        List<FormalParameter> boundVars = parseParameterList(scanner, false);
        match(TokenNameLBRACE, scanner);
        ConstraintExpression body = parseConstraintExpr(scanner);
        SourceLocation location = start.extendedTo(scanner.getCurrentLocation());
        match(TokenNameRBRACE, scanner);
        return new ExistentialQuantifier(location, boundVars, body);
    }

    // forall (Type t : targetType(t)) { hasSignature(visitorInterface, [...]) }
    // forall (Type t : ...) { ... } onfail { "text" }
    private UniversalQuantifier parseForallExpression(BacktrackingScanner scanner) {
        SourceLocation start = scanner.getCurrentLocation();
        matchKeyword(FORALL_KEYWORD, scanner);
        List<FormalParameter> boundVars = parseParameterList(scanner, false);
        match(TokenNameCOLON, scanner);
        ConstraintExpression initialSet = parseConstraintExpr(scanner);
        match(TokenNameLBRACE, scanner);
        ConstraintExpression body = parseConstraintExpr(scanner);
        SourceLocation location = start.extendedTo(scanner.getCurrentLocation());
        match(TokenNameRBRACE, scanner);
        ErrorMessage errorMsg = parseOptionalOnFailMessage(scanner);
        if (errorMsg == null) {
            errorMsg = ErrorMessage.EMPTY_MESSAGE;
        }
        return new UniversalQuantifier(location, boundVars, initialSet, body, errorMsg);
    }

    // stmt == select {
    //   targetType(toType): <visitor.visit(this.`edge);>,
    //   else: <this.`edge.`traversalName(visitor);>
    // }
    private ConstraintExpression parseSelectExpression(BacktrackingScanner scanner) {
        SourceLocation start = scanner.getCurrentLocation();
        matchKeyword(SELECT_KEYWORD, scanner);
        match(TokenNameLBRACE, scanner);
        List<ConstraintExpression> conditions = Lists.newArrayList();
        List<ConstraintExpression> values = Lists.newArrayList();
        while (scanner.lookaheadIsNoneOf(TokenNameelse)) {
            ConstraintExpression condition = parseConstraintExpr(scanner);
            match(TokenNameCOLON, scanner);
            ConstraintExpression value = parseBooleanFactor(scanner,
                ParseMode.VALUES_ONLY);
            match(TokenNameCOMMA, scanner);
            conditions.add(condition);
            values.add(value);
        }
        match(TokenNameelse, scanner);
        match(TokenNameCOLON, scanner);
        ConstraintExpression defaultValue = parseConstraintExpr(scanner);
        if (scanner.lookaheadEquals(TokenNameCOMMA)) {
            // final comma is optional
            match(scanner);
        }
        values.add(defaultValue);

        SourceLocation location = start.extendedTo(scanner.getCurrentLocation());
        match(TokenNameRBRACE, scanner);

        return new SelectExpression(location, conditions, values);
    }

    private PatternExpression parsePatternExpression(BacktrackingScanner scanner,
        int leftDelim, int rightDelim)
    {
        int startPosition = scanner.getStartPosition() + 1;
        SourceLocation patStartLoc = scanner.getCurrentLocation();

        List<EmbeddedExpression> embeddedExpressions = Lists.newArrayList();

        int nesting = 0;
        eatTokens: for (;;) {
            if (scanner.lookaheadEquals(leftDelim)) {
                ++nesting;
            }
            else if (scanner.lookaheadEquals(rightDelim)) {
                --nesting;
                if (nesting == 0) {
                    break eatTokens;
                }
            }
            else if (scanner.lookaheadEquals(TokenNameARCUMBEGINQUOTE)) {
                embeddedExpressions.add(parseEmbeddedExpressionBody(this, scanner));
            }
            else if (scanner.lookaheadEquals(TokenNameEOF)) {
                ++numErrors;
                ArcumError.fatalUserError(patStartLoc,
                    "Reached EOF before corresponding \']\' was found");
                break eatTokens;
            }
            match(scanner);
        }
        int length = scanner.getStartPosition() - startPosition;
        SourceLocation patEndLoc = scanner.getCurrentLocation();
        match(rightDelim, scanner);
        SourceLocation location = patStartLoc.extendedTo(patEndLoc);
        String text = scanner.getText(startPosition, length);
        return new PatternExpression(location, text, this, embeddedExpressions);
    }

    public class EmbeddedExpression
    {
        private final FormalParameter boundVar;
        private final ConstraintExpression constraintExpression;
        private int count;

        private EmbeddedExpression(BacktrackingScanner scanner) {
            scanner.match(TokenNameARCUMBEGINQUOTE);
            this.count = 0;
            TokenCountListener counter = new TokenCountListener() {
                public void count() {
                    ++EmbeddedExpression.this.count;
                }
            };
            scanner.addTokenCountListener(counter);
            this.boundVar = parseFormal(scanner, false);
            match(TokenNameCOLON, scanner);
            this.constraintExpression = parseConstraintExpr(scanner);
            match(TokenNameRBRACKET, scanner);
            scanner.removeTokenCountListener(counter);
        }

        public FormalParameter getBoundVar() {
            return boundVar;
        }

        public ConstraintExpression getConstraintExpression() {
            return constraintExpression;
        }

        public int getNumTokens() {
            return count;
        }
    }

    public static EmbeddedExpression parseEmbeddedExpressionBody(
        ArcumStructureParser thiz, BacktrackingScanner scanner)
    {
        return thiz.new EmbeddedExpression(scanner);
    }

    private FunctionalExpression parseBooleanFunction(SourceLocation startLocation,
        String name, BacktrackingScanner scanner)
    {
        match(TokenNameLPAREN, scanner);
        List<ConstraintExpression> args = parseFunctionArgumentList(scanner);
        SourceLocation end = scanner.getCurrentLocation();
        SourceLocation wholeExpression = startLocation.extendedTo(end);
        match(TokenNameRPAREN, scanner);
        IFunction function = BuiltInFunction.lookup(name);
        if (function == BuiltInFunction.STATIC_TRAIT) {
            function = new TraitFunction(name);
        }
        return new FunctionalExpression(wholeExpression, function, args);
    }

    private List<ConstraintExpression> parseFunctionArgumentList(
        BacktrackingScanner scanner)
    {
        List<ConstraintExpression> result = Lists.newArrayList();
        for (;;) {
            ConstraintExpression arg = parseBooleanFactor(scanner,
                ParseMode.FUNCTION_ARGUMENTS);
            result.add(arg);
            if (scanner.lookahead() != TokenNameCOMMA)
                break;
            match(TokenNameCOMMA, scanner);
        }
        return result;
    }

    private List<FormalParameter> parseParameterList(BacktrackingScanner scanner,
        boolean allowSubtraits)
    {
        List<FormalParameter> result = new ArrayList<FormalParameter>();
        match(TokenNameLPAREN, scanner);
        if (scanner.lookaheadIsNoneOf(TokenNameRPAREN, TokenNameLBRACE)) {
            for (;;) {
                FormalParameter formal = parseFormal(scanner, allowSubtraits);
                result.add(formal);
                if (scanner.lookahead() != TokenNameCOMMA)
                    break;
                match(TokenNameCOMMA, scanner);
            }
        }
        match(TokenNameRPAREN, scanner);
        return result;
    }

    // E.g, the formals in:
    // singleton VisitorConcept(
    //   Type rootType,
    //   targetType(Type type),
    //   viaEdge(Field field) default isField(field),
    //   bypassEdge(Field field) default false,
    //   sourceScope(Package pack) default onBuildPath(pack));
    private FormalParameter parseFormal(BacktrackingScanner scanner,
        boolean allowSubtraits)
    {
        FormalParameter formal;

        String lexeme = scanner.getCurrentTokenString();
        SourceLocation lexemePosition = scanner.getCurrentLocation();
        match(TokenNameIdentifier, scanner);
        if (scanner.lookahead() == TokenNameLPAREN) {
            String subtraitName = lexeme;
            List<FormalParameter> traitArguments = parseParameterList(scanner, false);
            formal = new FormalParameter(EntityType.TRAIT, subtraitName);
            formal.addTraitArguments(traitArguments);
            if (!allowSubtraits) {
                SourceLocation location = scanner.getCurrentLocation();
                ++numErrors;
                ArcumError.fatalUserError(location,
                    "Sub-traits are (currently) only permitted for option constructors");
            }
        }
        else {
            EntityType type = getEntityType(lexeme, lexemePosition);
            String name = scanner.getCurrentTokenString();
            match(TokenNameIdentifier, scanner);
            formal = new FormalParameter(type, name);
        }

        if (scanner.lookaheadEquals(TokenNamedefault)) {
            match(scanner);
            if (scanner.lookaheadEquals(TokenNameStringLiteral)) {
                formal.addDefaultValue(scanner.getCurrentStringLiteral());
            }
            else {
                ConstraintExpression defaultBody = parseConstraintExpr(scanner);
                formal.addDefaultValue(defaultBody);
            }
        }

        return formal;
    }

    private EntityType getEntityType(String lexeme, SourceLocation location) {
        EntityType type = EntityType.lookup(lexeme);
        if (type == EntityType.ERROR) {
            ++numErrors;
            ArcumError.fatalUserError(location, "Expected \"%s\" to be a type name",
                lexeme);
        }
        return type;
    }

    private void parseOption(ArcumDeclarationTable table, String importsString,
        BacktrackingScanner scanner)
    {
        String name = scanner.getCurrentTokenString();
        SourceLocation location = scanner.getCurrentLocation();
        match(TokenNameIdentifier, scanner);
        match(TokenNameimplements, scanner);
        String trait = scanner.getCurrentTokenString();
        match(TokenNameIdentifier, scanner);
        Option option = Option.newOption(name, trait, importsString, table, location);
        match(TokenNameLBRACE, scanner);
        parseOptionMembers(option, scanner);
        match(TokenNameRBRACE, scanner);
    }

    // Clauses that start with "realize", "define" or free-standing "require"
    private void parseOptionMembers(Option option, BacktrackingScanner scanner) {
        Parsing: for (;;) {
            if (arcumKeyword(REALIZE_KEYWORD, scanner)
                || arcumKeyword(DEFINE_KEYWORD, scanner))
            {
                parseRealizationStatement(option, scanner);
            }
            else if (isFreeStandingRequirementStart(scanner)) {
                FreeStandingRequirements requirements;
                requirements = option.getFreeStandingRequirements();
                parseFreeStandingRequirements(scanner, requirements);
            }
            else if (scanner.lookahead() == TokenNameRBRACE) {
                break Parsing;
            }
            else {
                // EXAMPLE: Before each call to fatalUserError, increment error count
                ++numErrors;
                ArcumError.fatalUserError(scanner.getCurrentLocation(),
                    "Expected \"realize\", \"define\" or \"require\" keyword");
            }
        }
    }

    private boolean isFreeStandingRequirementStart(BacktrackingScanner scanner) {
        return arcumKeyword(REQUIRE_KEYWORD, scanner);
    }

    private void parseFreeStandingRequirements(BacktrackingScanner scanner,
        FreeStandingRequirements requirements)
    {
        if (!arcumKeyword(REQUIRE_KEYWORD, scanner)) {
            ArcumError.fatalError("Internal error in parseFreeStandingRequirements");
        }

        matchKeyword(REQUIRE_KEYWORD, scanner);
        ErrorMessage message;
        message = parseErrorMessageText(scanner);
        match(TokenNameCOLON, scanner);
        ConstraintExpression expr = parseConstraintExpr(scanner);
        match(TokenNameSEMICOLON, scanner);
        requirements.addRequiresClause(expr, message);
    }

    // E.g.,
    // realize id(args) { expr }
    // define id(args) { expr }
    private RealizationStatement parseRealizationStatement(TopLevelConstruct declaration,
        BacktrackingScanner scanner)
    {
        SourceLocation location = scanner.getCurrentLocation();
        RealizationStatement stmt;
        if (arcumKeyword(DEFINE_KEYWORD, scanner)) {
            // e.g.:
            // define properAccess(Expr call, Expr e) {
            //   call == ([`e.put(`_, `_)] || [`e.get(`_)])
            // }
            match(scanner);
            String name = scanner.getCurrentTokenString();
            match(TokenNameIdentifier, scanner);
            List<FormalParameter> params = parseParameterList(scanner, false);
            match(TokenNameLBRACE, scanner);
            ConstraintExpression patternExpr = parseConstraintExpr(scanner);
            stmt = StaticRealizationStatement.makeStatic(declaration, name, patternExpr,
                params, location);
        }
        else {
            // e.g.:
            // realize attrGet(getExpr, targetExpr) {
            //   getExpr == [`targetType.`mapField.get(`targetExpr)]
            // }
            matchKeyword(ArcumKeyword.REALIZE_KEYWORD, scanner); // eat "realize" keyword
            stmt = new RealizationStatement(declaration, location);
            parseRealizedTupleSetList(stmt, declaration, scanner);
            match(TokenNameLBRACE, scanner);
            ConstraintExpression bodyExpr = parseConstraintExpr(scanner);
            stmt.setBodyExpression(bodyExpr);
        }
        parseOptionalRequireClauses(scanner, stmt);
        stmt.setPosition(location.extendedTo(scanner.getCurrentLocation()));
        stmt.verifyValidVariables();
        match(TokenNameRBRACE, scanner);
        ErrorMessage optionalOnFailMessage = parseOptionalOnFailMessage(scanner);
        if (optionalOnFailMessage != null) {
            stmt.addSingletonErrorMessage(optionalOnFailMessage);
        }
        return stmt;
    }

    // parse a sequence of require clauses, e.g.:
    //   require "The name `attrName must be a valid Java identifier:
    //     isJavaIdentifier(attrName);
    // 
    //   require "The attribute type cannot be a primitive type":
    //     isReferenceType(attrType);
    //
    // Errors occur when the condition after the requirement keyword fails.
    private void parseOptionalRequireClauses(BacktrackingScanner scanner,
        Constrainable constrainable)
    {
        scanner.matchIfPresent(TokenNameSEMICOLON); // Eat any leading semi-colons
        if (arcumKeyword(REQUIRE_KEYWORD, scanner)) {
            while (arcumKeyword(REQUIRE_KEYWORD, scanner)) {
                match(TokenNameIdentifier, scanner);
                ErrorMessage message;
                if (this.isErrorMessageTextStart(scanner)) {
                    message = parseErrorMessageText(scanner);
                    match(TokenNameCOLON, scanner);
                }
                else {
                    message = ErrorMessage.EMPTY_MESSAGE;
                }
                ConstraintExpression expr = parseConstraintExpr(scanner);
                constrainable.addRequiresClause(expr, message);
                scanner.matchIfPresent(TokenNameSEMICOLON);
            }
        }
    }

    private void parseRealizedTupleSetList(RealizationStatement stmt,
        TopLevelConstruct declaration, BacktrackingScanner scanner)
    {
        parseRealizedTupleSet(stmt, declaration, scanner);
        while (scanner.lookahead() == TokenNameCOMMA) {
            match(TokenNameCOMMA, scanner);
            parseRealizedTupleSet(stmt, declaration, scanner);
        }
    }

    private void parseRealizedTupleSet(RealizationStatement stmt,
        TopLevelConstruct declaration, BacktrackingScanner scanner)
    {
        SourceLocation startLocation = scanner.getCurrentLocation();
        String typeOrTraitName = scanner.getCurrentTokenString();
        match(TokenNameIdentifier, scanner);
        // trait -- e.g.: realize visit(Expr root, Expr target, Expr visitor) with
        if (scanner.lookaheadEquals(TokenNameLPAREN)) {
            String traitName = typeOrTraitName;
            List<FormalParameter> formals = parseParameterList(scanner, false);
            TraitSignature signature = TraitSignature.makeTraitSignature(traitName,
                formals);
            stmt.addTraitSignature(signature);
        }
        // singleton -- e.g.: realize Field field, AccessSpecifier spec with
        else if (scanner.lookaheadEquals(TokenNameIdentifier)) {
            {
                String typeName = typeOrTraitName;
                String varName = scanner.getCurrentTokenString();
                match(TokenNameIdentifier, scanner);
                EntityType type = getEntityType(typeName, startLocation);
                TraitSignature singleton = TraitSignature.makeSingleton(varName,
                    new FormalParameter(type, varName));
                stmt.addTraitSignature(singleton);
            }
            while (scanner.lookaheadEquals(TokenNameCOMMA)) {
                match(TokenNameCOMMA, scanner);
                String typeName = scanner.getCurrentTokenString();
                match(TokenNameIdentifier, scanner);
                String varName = scanner.getCurrentTokenString();
                match(TokenNameIdentifier, scanner);
                EntityType type = getEntityType(typeName, startLocation);
                TraitSignature singleton = TraitSignature.makeSingleton(varName,
                    new FormalParameter(type, varName));
                stmt.addTraitSignature(singleton);
            }
        }
        else {
            ArcumError.fatalUserError(scanner.getCurrentLocation(),
                "Expected either a \'(\' or an identifier");
            return;
        }
    }

    // require {
    //  InternalField(targetType=User, attrType=String, attrName="nickName");
    // }
    private void parseTopLevelRequire(ArcumDeclarationTable table, String importsString,
        IProject project, BacktrackingScanner scanner)
    {
        RequireMap map = RequireMap.newArcumMap(importsString, project, table, resource);
        match(TokenNameLBRACE, scanner);
        parseMapMembers(map, scanner);
        match(TokenNameRBRACE, scanner);
    }

    private void parseMapMembers(RequireMap map, BacktrackingScanner scanner) {
        Parsing: for (;;) {
            if (scanner.lookahead() == TokenNameIdentifier) {
                String optionUsed;
                int start;
                int end;
                int length;
                List<MapNameValueBinding> bindings;
                String optionClauseText;
                SourceLocation location;

                optionUsed = scanner.getCurrentTokenString();
                start = scanner.getCurrentTokenStartPosition();
                match(TokenNameIdentifier, scanner);
                match(TokenNameLPAREN, scanner);
                bindings = parseMapNameValuePairs(map, scanner);
                match(TokenNameRPAREN, scanner);
                end = scanner.getCurrentTokenStartPosition();
                length = end - start;
                optionClauseText = scanner.getText(start, length);
                location = new SourceLocation(resource, start, end, scanner
                    .getLineNumber(start));
                map.addBindings(optionUsed, bindings, optionClauseText, location);
                match(TokenNameSEMICOLON, scanner);
            }
            else {
                break Parsing;
            }
        }
    }

    // e.g.,
    //   traversalName: "visitBooks",
    //   rootType: Library,
    //   targetType(type): type == ([Book] || [Paper])
    private List<MapNameValueBinding> parseMapNameValuePairs(RequireMap map,
        BacktrackingScanner scanner)
    {
        List<MapNameValueBinding> pairs = new ArrayList<MapNameValueBinding>();
        while (scanner.lookahead() == TokenNameIdentifier) {
            String name = scanner.getCurrentTokenString();
            SourceLocation nameStart = scanner.getCurrentLocation();
            match(TokenNameIdentifier, scanner);
            if (scanner.lookaheadEquals(TokenNameCOLON)) {
                // e.g., traversalName: "visitBooks"
                match(TokenNameCOLON, scanner);
                pairs.add(parseMapNameValueBinding(nameStart, name, scanner));
            }
            else if (scanner.lookaheadEquals(TokenNameLPAREN)) {
                // e.g., targetType(Type type): type == ([Book] || [Paper])
                List<FormalParameter> formals = parseParameterList(scanner, false);
                match(TokenNameCOLON, scanner);
                ConstraintExpression expr = parseConstraintExpr(scanner);
                SourceLocation curLoc = scanner.getCurrentLocation();
                SourceLocation location = nameStart.extendedTo(curLoc);
                MapNameValueBinding mta = new MapTraitArgument(location, map, name,
                    formals, expr);
                pairs.add(mta);
            }

            if (scanner.lookahead() != TokenNameCOMMA)
                break;
            match(TokenNameCOMMA, scanner);
        }
        return pairs;
    }

    // parses the binding (value) end of the name=value construct
    private MapNameValueBinding parseMapNameValueBinding(SourceLocation nameStart,
        String name, BacktrackingScanner scanner)
    {
        SourceLocation location;
        if (scanner.lookahead() == TokenNameStringLiteral) {
            String lit = scanner.getCurrentStringLiteral();
            location = nameStart.extendedTo(scanner.getCurrentLocation());
            match(scanner);
            return new MapStringArgument(location, name, lit);
        }
        else if (scanner.lookahead() == TokenNamefalse
            || scanner.lookahead() == TokenNametrue)
        {
            boolean value = scanner.lookahead() == TokenNametrue;
            location = nameStart.extendedTo(scanner.getCurrentLocation());
            match(scanner);
            return new MapBooleanArgument(location, name, value);
        }
        else {
            // NOTE: Code duplication:
            // consume tokens until we reach the end of the ambiguous value
            // in the future, we may need to allow nested parenthesis and commas
            // within them. But for now this is mostly to handle type names
            int startPositition = scanner.getStartPosition();
            location = nameStart.extendedTo(scanner.getCurrentLocation());
            while (scanner.lookaheadIsNoneOf(TokenNameCOMMA, TokenNameRPAREN,
                TokenNameEOF))
            {
                location = nameStart.extendedTo(scanner.getCurrentLocation());
                match(scanner);
            }
            int length = scanner.getStartPosition() - startPositition;
            String text = scanner.getText(startPositition, length);
            return new MapAmbiguousArgument(location, name, text);
        }
    }

    private void matchKeyword(ArcumKeyword keyword, BacktrackingScanner scanner) {
        if (arcumKeyword(keyword, scanner)) {
            match(TokenNameIdentifier, scanner);
        }
        else {
            ++numErrors;
            ArcumError.fatalUserError(scanner.getCurrentLocation(),
                "Expected keyword \"%s\"", keyword);
        }
    }

    private boolean arcumKeyword(ArcumKeyword keyword, BacktrackingScanner scanner) {
        if (scanner.lookaheadEquals(TokenNameIdentifier)) {
            String lexeme = scanner.getCurrentTokenString();
            return lexeme.equals(keyword.getLexeme());
        }
        return false;
    }

    // for testing/debugging
    // private void outputTokens(char[] sourceString) {
    // Scanner scanner = ArcumSourceFileParser.newScanner(sourceString);
    // for (;;) {
    // int token = nextToken(scanner);
    // if (token == TokenNameEOF)
    // break;
    // System.out.printf("\"%s\" (code: %d)%n", scanner
    // .getCurrentTokenString(), token);
    // }
    // }

    private void match(BacktrackingScanner scanner) {
        scanner.match();
    }

    private void match(int expectedToken, BacktrackingScanner scanner) {
        if (!scanner.lookaheadEquals(expectedToken)) {
            SourceLocation location = scanner.getCurrentLocation();
            if (scanner.lookaheadEquals(TokenNameEOF)) {
                ++numErrors;
                ArcumError.fatalUserError(location, "Unexpected EOF");
            }
            else {
                String message = String.format("Expected \"%s\" instead of \"%s\".",
                    tokenToString(expectedToken), tokenToString(scanner.lookahead()));
                if (location != null) {
                    ++numErrors;
                    ArcumError.fatalUserError(location, "%s", message);
                }
                else {
                    System.out.printf("%s", scanner);
                    ++numErrors;
                    ArcumError.fatalError("%s", message);
                }
            }
        }
        scanner.nextToken();
    }

    private int nextToken(BacktrackingScanner scanner) {
        return scanner.nextToken();
    }

    // Returns the number of parser errors found
    public int getNumErrors() {
        return numErrors;
    }

    public IResource getResource() {
        return resource;
    }
}