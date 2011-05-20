package edu.ucsd.arcum.interpreter.parser;

import static edu.ucsd.arcum.exceptions.ArcumError.fatalUserError;
import static edu.ucsd.arcum.interpreter.parser.TokenData.tokenToString;
import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.TokenNameEOF;
import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.TokenNameERROR;
import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.TokenNameIdentifier;
import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.TokenNameLBRACKET;

import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.parser.Scanner;

import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.SourceLocation;

@SuppressWarnings("restriction")
public class BacktrackingScanner
{
    public interface TokenCountListener
    {
        void count();
    }

    public static final int TokenNameARCUMVARIABLE = 2001;
    public static final int TokenNameARCUMBEGINQUOTE = 2002;

    private char[] buff;
    private IResource resource;
    private Scanner scanner;
    private int lookahead;
    private final Set<TokenCountListener> tokenCountListeners;

    public BacktrackingScanner(String contents) {
        this.buff = contents.toCharArray();
        this.tokenCountListeners = Sets.newHashSet();
        backtrack();
    }

    public BacktrackingScanner(String source, IResource resource) {
        this(source);
        this.resource = resource;
    }

    // revert the scanner to read from the start
    public void backtrack() {
        this.scanner = new Scanner(false/*tokenizeComments*/,
            false/*tokenizeWhiteSpace*/, false/*checkNSL*/,
            ClassFileConstants.JDK1_5/*sourceLevel*/, null/*taskTags*/,
            null/*taskPriorities*/, false/*isTaskCaseSensitive*/);
        scanner.setSource(buff);
        nextToken();
    }

    public String getCurrentTokenString() {
        return scanner.getCurrentTokenString();
    }

    // returns TokenNameARCUMVARIABLE in the event of a backtick variable
    // reference. The call to getCurrentTokenString can be used to get the
    // Arcum variable's name (minus the leading "`" tick).
    public int nextToken() {
        int token = _nextToken();
        if (token == TokenNameERROR) {
            String lexeme = scanner.getCurrentTokenString();
            if (!lexeme.equals("`")) {
                fatalUserError(getCurrentLocation(), "Unknown input character: %s",
                    lexeme);
            }
            token = _nextToken();
            if (token == TokenNameLBRACKET) {
                token = TokenNameARCUMBEGINQUOTE;
            }
            else if (token == TokenNameIdentifier) {
                token = TokenNameARCUMVARIABLE;
            }
            else {
                fatalUserError(getCurrentLocation(),
                    "No variable name specified after the \"`\"");
            }
        }
        this.lookahead = token;
        for (TokenCountListener tokenCountListener : tokenCountListeners) {
            tokenCountListener.count();
        }
        return token;
    }

    private int _nextToken() {
        try {
            return scanner.getNextToken();
        }
        catch (InvalidInputException e) {
            e.printStackTrace();
            System.err.printf("nextToken error%n");
            return TokenNameEOF;
        }
    }

    public int lookahead() {
        return lookahead;
    }

    public void matchIfPresent(int token) {
        if (lookahead == token) {
            match();
        }
    }

    public void match(int token) {
        if (lookahead != token) {
            fatalUserError(getCurrentLocation(), "Parsing error: Expected a %s%n",
                tokenToString(token));
        }
        match();
    }

    public void match() {
        nextToken();
    }

    public boolean lookaheadIsNoneOf(int... tokens) {
        for (int token : tokens) {
            if (lookahead == token) {
                return false;
            }
        }
        return true;
    }

    public boolean lookaheadEquals(int token) {
        return lookahead == token;
    }

    public int getCurrentTokenStartPosition() {
        return scanner.getCurrentTokenStartPosition();
    }

    public int getCurrentTokenEndPosition() {
        return scanner.getCurrentTokenEndPosition();
    }

    public String getCurrentStringLiteral() {
        return scanner.getCurrentStringLiteral();
    }

    public int getStartPosition() {
        // NOTE: in the case of `vars (TokenNameARCUMVARIABLE) this start
        // position won't be accurate -- this is currently worked around in
        // the parser, but would be cleaner if done here
        return scanner.startPosition;
    }

    public String getText(int startPositition, int length) {
        StringBuilder buff = new StringBuilder();
        buff.append(scanner.source, startPositition, length);
        return buff.toString();
    }

    // Eat tokens until we see our first 'token' or EOF; Returns what was
    // actually seen
    public int consumeUntil(int toFind) {
        int token;
        do {
            token = nextToken();
        } while (token != toFind && token != TokenNameEOF);
        return token;
    }

    public int getLineNumber(int position) {
        return scanner.getLineNumber(position);
    }

    public SourceLocation getCurrentLocation() {
        if (resource != null) {
            SourceLocation result = new SourceLocation(resource, scanner
                .getCurrentTokenStartPosition(),
                scanner.getCurrentTokenEndPosition() + 1, scanner
                    .getLineNumber(scanner.currentPosition));
            return result;
        }
        return null;
    }

    @Override public String toString() {
        return scanner.toString();
    }

    public boolean containsToken(int... tokens) {
        Set<Integer> lookup = Sets.newHashSet();
        for (int token : tokens) {
            lookup.add(token);
        }
        for (;;) {
            if (lookahead == TokenNameEOF)
                break;
            else if (lookup.contains(lookahead)) {
                return true;
            }
            match();
        }
        return false;
    }

    public void addTokenCountListener(TokenCountListener tokenCountListener) {
        tokenCountListeners.add(tokenCountListener);
    }
    
    public void removeTokenCountListener(TokenCountListener tokenCountListener) {
        tokenCountListeners.remove(tokenCountListener);
    }
}