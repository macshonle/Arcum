package edu.ucsd.arcum.interpreter.parser;

import static org.eclipse.jdt.internal.compiler.parser.TerminalTokens.*;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.IDocument;

import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.ui.editor.ConceptMapEntry;
import edu.ucsd.arcum.ui.editor.TopLevelSourceEntry;

@SuppressWarnings("restriction")
public class ArcumSourceFileParser
{   
    private String source;
    private IResource resource;
    private String sourcefileName;
    private IProject project;
    
    public static List<TopLevelSourceEntry> quickParse(IDocument document) {
        System.out.printf("quickParse%n");
        
        String source = document.get();

        BacktrackingScanner scanner = new BacktrackingScanner(source);
        String importsString = extractImportParts(scanner);

        List<TopLevelSourceEntry> result = new ArrayList<TopLevelSourceEntry>();
        DietParsing: for (;;) {
            while (scanner.lookaheadIsNoneOf(TokenNameIdentifier, TokenNameEOF)) {
                scanner.match();
            }
            if (scanner.lookahead() == TokenNameEOF) {
                break DietParsing;
            }
            else if (scanner.lookahead() == TokenNameIdentifier) {
                ArcumKeyword keyword = ArcumKeyword.lookup(scanner.getCurrentTokenString());
                if (keyword == ArcumKeyword.REQUIRE_KEYWORD) {
                    int returned = scanner.consumeUntil(TokenNameLBRACE);
                    if (returned == TokenNameEOF)
                        break DietParsing;
                    scanner.nextToken();
                    while (scanner.lookahead() == TokenNameIdentifier) {
                        String optionUsed;
                        int start;
                        int end;
                        int length;
                        String args;
                        
                        optionUsed = scanner.getCurrentTokenString();
                        start = scanner.getCurrentTokenStartPosition();
                        scanner.consumeUntil(TokenNameSEMICOLON);
                        end = scanner.getCurrentTokenStartPosition();
                        length = end - start;
                        scanner.match();
                        args = scanner.getText(start, length);
                        result.add(new ConceptMapEntry(document, optionUsed, args));
                    }
                    consumeNextMatchedCurlies(scanner);
                }
                else if (keyword == ArcumKeyword.OPTION_KEYWORD) {
                    // NOTE: We don't show this structure yet, instead we just
                    // skip it to go to the next map. A better strategy would
                    // allow traits to be seen too, but that would require a
                    // hierarchial structure and a better strategy for determining
                    // when to reparse (all doable, but just not a priority)
//                    int t = scanner.nextToken();
//                    if (t == TokenNameinterface) {
//                        t = scanner.nextToken();
//                    }
//                    if (t == TokenNameIdentifier) {
//                        String id = scanner.getCurrentTokenString();
//                        result.add(new OptionOrConceptDeclaration(id));
//                    }
                    consumeNextMatchedCurlies(scanner);
                }
            }
        }
        return result;
    }

    public ArcumSourceFileParser(String source, IResource resource,
            String name, IProject project)
    {
        this.source = source;
        this.resource = resource;
        this.sourcefileName = name;
        this.project = project;
    }
    
    // Parses each segment of the .arcum file, the first segment is the list of
    // imports, followed by trait, option or requirement map definitions. Returns
    // the number of parse errors encountered.
    public int parseArcumSource(ArcumDeclarationTable table) {
        int numErrors = 0;
        try {
            BacktrackingScanner scanner = new BacktrackingScanner(source, resource);
            String importsString = extractImportParts(scanner);

            ArcumStructureParser structParser;
            structParser = new ArcumStructureParser(resource, project, importsString);
            while (scanner.lookaheadIsNoneOf(TokenNameEOF)) {
                structParser.parse(scanner, table);
            }
            numErrors += structParser.getNumErrors();
        }
        catch (Exception e) {
            ++numErrors;
            e.printStackTrace();
        }
        return numErrors;
    }

    // Read all tokens until the first "{" or EOF is found. Then, the position
    // of the imports is from the start of the buffer up until and including
    // the last ";" read.
    private static String extractImportParts(BacktrackingScanner scanner) {
        int lastSemi = -1;
        for (;;) {
            int token = scanner.lookahead();
            if (token == TokenNameEOF || token == TokenNameLBRACE)
                break;
            if (token == TokenNameSEMICOLON)
                lastSemi = scanner.getCurrentTokenStartPosition();
            scanner.match();
        }
        scanner.backtrack();
        String result = String.format("import java.lang.*;%n");
        if (lastSemi != -1) {
            while (scanner.getCurrentTokenStartPosition() < lastSemi)
                scanner.match();
            scanner.match();
            return result + scanner.getText(0, scanner.getCurrentTokenStartPosition());
        }
        return result;
    }

    // Reads in all tokens until EOF or a matched pair of {}'s is read.
    // Returns the last token read, which will either be EOF or RBRACE.
    private static int consumeNextMatchedCurlies(BacktrackingScanner scanner) {
        int token = scanner.consumeUntil(TokenNameLBRACE);
        
        // eat more tokens, until the "}" is closed
        int nesting = 0;
        for (;;) {
            if (token == TokenNameLBRACE) {
                ++nesting;
            }
            else if (token == TokenNameRBRACE) {
                --nesting;
                if (nesting == 0)
                    return TokenNameRBRACE;
            }
            else if (token == TokenNameEOF) {
                return TokenNameEOF;
            }
            token = scanner.nextToken();
        }
    }
}