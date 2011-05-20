package edu.ucsd.arcum.interpreter.transformation;

import java.util.List;

import org.eclipse.core.resources.IFile;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.ast.MapNameValueBinding;
import edu.ucsd.arcum.interpreter.ast.Option;
import edu.ucsd.arcum.util.StringUtil;

public class ResolvedConceptMapEntry {
    private Option option;
    private List<MapNameValueBinding> arguments;
    private String argsText;
    private SourceLocation location;
    private IFile sourceFile;

    // location passed in is the entry in the map file that asserts the given
    // option is present in the program
    public ResolvedConceptMapEntry(Option option,
            List<MapNameValueBinding> arguments, String argsText,
            SourceLocation location, IFile sourceFile)
    {
        this.option = option;
        this.arguments = arguments;
        this.argsText = argsText;
        this.location = location;
        this.sourceFile = sourceFile;
    }

    public List<MapNameValueBinding> getArguments() {
        return arguments;
    }

    public Option getOption() {
        return option;
    }

    public String getArgumentSourceText() {
        return argsText;
    }
    
    public String getDisplayString() {
        return StringUtil.minimizeWhitespace(argsText);
    }

    public SourceLocation getLocation() {
        return location;
    }

    public IFile getSourceFile() {
        // MACNEIL: could be redundant with the SourceLocation information we
        // already have
        return sourceFile;
    }
}