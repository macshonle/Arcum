package edu.ucsd.arcum.interpreter.ast;

import edu.ucsd.arcum.exceptions.SourceLocation;

public abstract class MapNameValueBinding
{
    private SourceLocation location;
    private String name;
    private String optionClauseText;
    
    protected MapNameValueBinding(SourceLocation location, String name) {
        this.name = name;
        this.location = location;
    }

    public String getName() {
        return name;
    }
    
    public abstract String toString();

    public abstract Object getValue();

    public final SourceLocation getLocation() {
        return location;
    }

    public final void setOptionClauseText(String optionClauseText) {
        this.optionClauseText = optionClauseText;
    }
}