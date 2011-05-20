package edu.ucsd.arcum.ui.views;

import edu.ucsd.arcum.interpreter.fragments.Union;
import edu.ucsd.arcum.interpreter.query.Entity;

public class EntityTableSingletonElement extends EntityTableSubElement
{
    private String name;
    private @Union("Entity") Object entity;

    public EntityTableSingletonElement(String name, @Union("Entity") Object entity,
            EntityTableFolder parent)
    {
        super(parent, Entity.getASTNodeValue(entity));
        this.name = name;
        this.entity = entity;
    }

    @Override public String toString() {
        return Entity.getDisplayString(entity);
    }
    
    @Override
    protected String getNameColumnContents() {
        return name;
    }
}
