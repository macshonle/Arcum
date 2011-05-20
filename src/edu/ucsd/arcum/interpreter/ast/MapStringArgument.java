package edu.ucsd.arcum.interpreter.ast;

import edu.ucsd.arcum.exceptions.SourceLocation;

public class MapStringArgument extends MapNameValueBinding
{
    private String literal;

    public MapStringArgument(SourceLocation location, String name, String literal) {
        super(location, name);
        this.literal = literal;
    }

    public String getLiteral() {
        return literal;
    }
    
    @Override
    public String toString() {
        return getName() + ": \"" + literal + "\"";
    }

    @Override
    public Object getValue() {
        return literal;
    }
}
