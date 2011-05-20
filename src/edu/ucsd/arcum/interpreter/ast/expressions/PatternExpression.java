package edu.ucsd.arcum.interpreter.ast.expressions;

import static edu.ucsd.arcum.interpreter.parser.ArcumStructureParser.parseEmbeddedExpressionBody;
import static edu.ucsd.arcum.interpreter.parser.BacktrackingScanner.TokenNameARCUMBEGINQUOTE;
import static edu.ucsd.arcum.interpreter.parser.BacktrackingScanner.TokenNameARCUMVARIABLE;
import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.TokenNameEOF;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.parser.ArcumStructureParser;
import edu.ucsd.arcum.interpreter.parser.BacktrackingScanner;
import edu.ucsd.arcum.interpreter.parser.ArcumStructureParser.EmbeddedExpression;

// A predicate-like statement, but uses Java code patterns instead
public class PatternExpression extends ConstraintExpression
{
    private final String pattern;
    private final ArcumStructureParser parser;
    private final List<EmbeddedExpression> embeddedExpressions;
    private final Set<String> references;
    private boolean isImmediate;

    public PatternExpression(SourceLocation location, String pattern,
        ArcumStructureParser parser, List<EmbeddedExpression> embeddedExpressions)
    {
        super(location);
        this.pattern = pattern;
        this.parser = parser;
        this.embeddedExpressions = Lists.newArrayList(embeddedExpressions);
        this.references = Sets.newHashSet();
        this.isImmediate = false;
        scanPattern();
    }

    private void scanPattern() {
        BacktrackingScanner scanner = new BacktrackingScanner(pattern, parser
            .getResource());
        for (;;) {
            if (scanner.lookahead() == TokenNameEOF) {
                break;
            }
            else if (scanner.lookahead() == TokenNameARCUMVARIABLE) {
                references.add(scanner.getCurrentTokenString());
                scanner.match();
            }
            else if (scanner.lookaheadEquals(TokenNameARCUMBEGINQUOTE)) {
                EmbeddedExpression embed = parseEmbeddedExpressionBody(parser, scanner);
                ConstraintExpression expr = embed.getConstraintExpression();
                Set<String> set = Sets.newHashSet(expr.getArcumVariableReferences());
                set.remove(embed.getBoundVar().getIdentifier());
                references.addAll(set);
            }
            else {
                scanner.match();
            }
        }
    }

    public boolean hasEmbeddedExpressions() {
        return embeddedExpressions.size() > 0;
    }

    public List<EmbeddedExpression> getEmbeddedExpressions() {
        return embeddedExpressions;
    }

    public EmbeddedExpression getEmbeddedExpression(int index) {
        return embeddedExpressions.get(index);
    }

    @Override public String toString() {
        String fmt = (isImmediate) ? "<%s>" : "[%s]";
        return String.format(fmt, pattern);
    }

    public String getPattern() {
        return pattern;
    }

    // scans the pattern string for Arcum variable references (those variables
    // in patterns that start with a '`' tick) and returns a set of them
    @Override public Set<String> getArcumVariableReferences() {
        return references;
    }

    @Override public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != PatternExpression.class) {
            return false;
        }
        else {
            PatternExpression that = (PatternExpression)obj;
            return this.pattern.equals(that.pattern);
        }
    }

    @Override protected void doCheckUserDefinedPredicates(List<TraitSignature> tupleSets,
        Set<String> varsInScope)
    {
        for (EmbeddedExpression embeddedExpression : embeddedExpressions) {
            ConstraintExpression expr = embeddedExpression.getConstraintExpression();
            Set<String> nextScope = Sets.newHashSet(varsInScope);
            nextScope.add(embeddedExpression.getBoundVar().getIdentifier());
            expr.doCheckUserDefinedPredicates(tupleSets, nextScope);
        }
        for (String reference : references) {
            if (!varsInScope.contains(reference)) {
                ArcumError.fatalUserError(getPosition(),
                    "Reference to undefined variable %s (check spelling or scope)",
                    reference);
            }
        }
    }

    public boolean isImmediatePattern() {
        return isImmediate;
    }

    public void setImmediate(boolean isImmediate) {
        this.isImmediate = isImmediate;
    }

    @Override public Set<String> findAllTraitDependencies() {
        Set<String> result = Sets.newHashSet();
        for (EmbeddedExpression embeddedExpression : embeddedExpressions) {
            ConstraintExpression expr = embeddedExpression.getConstraintExpression();
            FormalParameter boundVar = embeddedExpression.getBoundVar();
            Set<String> dependencies = expr.findAllTraitDependencies();
            dependencies.remove(boundVar.getIdentifier());
            result.addAll(dependencies);
        }
        result.addAll(references);// TASK
        return result;
    }

    // Because Java patterns can contain quoted Arcum code, which may compose lists
    // of expressions, they will need to be fully evaluated first
    @Override public Set<String> findNonMonotonicDependencies() {
        return this.findAllTraitDependencies();
    }
}