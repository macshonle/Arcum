package edu.ucsd.arcum.interpreter.ast;

import edu.ucsd.arcum.exceptions.SourceLocation;

public class MapBooleanArgument extends MapNameValueBinding
{
    private boolean value;

    public MapBooleanArgument(SourceLocation location, String name,
            boolean value)
    {
        super(location, name);
        this.value = value;
    }

    @Override
    public String toString() {
        return getName() + ": " + value;
    }

    @Override
    public Object getValue() {
        return value;
    }
}
