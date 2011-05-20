package edu.ucsd.arcum.interpreter.ast;

import org.eclipse.jdt.core.dom.ITypeBinding;

import edu.ucsd.arcum.exceptions.SourceLocation;

public class MapTypeArgument extends MapNameValueBinding
{
    private String qualifiedTypeName;
    private ITypeBinding type;

    public MapTypeArgument(SourceLocation location, String name, ITypeBinding type) {
        super(location, name);
//#        this.entity = new Entity(EntityType.TYPE, type);
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("%s: \'%s\'", getName(), type.getQualifiedName());
    }

    @Override
    public Object getValue() {
        return type;
    }
}
