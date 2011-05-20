package edu.ucsd.arcum.ui.views;

import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.EntityTuple;

public class EntityTableTraitElement extends EntityTableSubElement implements
        Comparable<EntityTableTraitElement>
{
    private EntityTuple entityTuple;

    public EntityTableTraitElement(EntityTuple entityTuple, EntityTableFolder parent)
    {
        super(parent, entityTuple.getMainDisplayNode());
        this.entityTuple = entityTuple;
    }

    // Lexically compares:
    //  * if the trait is static or not (non-static traits go first)
    //  * the names of the trait,
    //  * the names of the text files,
    //  * and then by start position.
    public int compareTo(EntityTableTraitElement that) {
        TraitSignature thizType = this.entityTuple.getType();
        TraitSignature thatType = that.entityTuple.getType();
        
        if (thizType.isStaticDefinition() != thatType.isStaticDefinition()) {
            int result = thizType.isStaticDefinition() ? -1 : 1;
            return result;
        }
        
        String thisName = this.getTraitName();
        String thatName = that.getTraitName();

        int result = thisName.compareTo(thatName);
        if (result == 0) {
            String thisFile = this.getPathName();
            String thatFile = that.getPathName();

            result = thisFile.compareTo(thatFile);
            if (result == 0) {
                if (this.node != null && that.node != null) {
                    Integer thisStart = this.node.getStartPosition();
                    Integer thatStart = that.node.getStartPosition();
                    result = thisStart.compareTo(thatStart);
                }
                else {
                    // XXX (!!!): Try something else with the node values: possibly
                    // node needs to be changed to Entity instead, with better
                    // support
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return entityTuple.toDisplayOfMembersString();
    }
    
    @Override
    protected String getNameColumnContents() {
        return getTraitName();
    }

    private String getTraitName() {
        return entityTuple.getType().getName();
    }
}