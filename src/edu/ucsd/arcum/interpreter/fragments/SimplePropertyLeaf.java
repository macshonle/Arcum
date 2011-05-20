package edu.ucsd.arcum.interpreter.fragments;

import org.eclipse.jdt.core.dom.AST;

import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;

public class SimplePropertyLeaf extends ProgramFragment
{
    private Object value;

    public SimplePropertyLeaf(Object value) {
        this.value = value;
    }

    @Override protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append(value.toString());
    }

    @Override protected BindingMap matchesSimpleProperty(Object simple) {
        if (simple == value || simple.equals(value)) {
            BindingMap result = bindRoot(simple);
            return result;
        }
        else {
            return null;
        }
    }

    @Override protected BindingMap matchesEmpty(EmptyEntityInfo entity) {
        if (value == null) {
            return BindingMap.newEmptyMap();
        }
        return null;
    }

    @Override public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        return bindRoot(value);
    }
}
