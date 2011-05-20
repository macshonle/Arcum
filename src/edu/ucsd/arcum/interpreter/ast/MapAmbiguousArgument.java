package edu.ucsd.arcum.interpreter.ast;

import edu.ucsd.arcum.exceptions.SourceLocation;

public class MapAmbiguousArgument extends MapNameValueBinding
{
    private String body;

    public MapAmbiguousArgument(SourceLocation location, String name, String body) {
        super(location, name);
        this.body = body;
    }

    @Override
    public String toString() {
        return getName() + ": \'" + body + "\'";
    }

    public String getBody() {
        return body;
    }

    @Override
    public Object getValue() {
        throw new RuntimeException("Value read on an ambiguous element");
    }
}