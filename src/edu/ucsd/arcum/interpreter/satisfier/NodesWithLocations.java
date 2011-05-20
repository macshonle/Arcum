package edu.ucsd.arcum.interpreter.satisfier;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;
import edu.ucsd.arcum.interpreter.query.EntityTuple;
import edu.ucsd.arcum.interpreter.query.TraitValue;
import edu.ucsd.arcum.util.StringUtil;

// Locations are determined by the built-in traits like hasField and hasMethod, which
// can be "run in both directions."
public class NodesWithLocations
{
    private List<EntityTuple> nodes;
    private List<TraitValue> locations;

    // nodeLocations are assumed to be built-in traits
    public NodesWithLocations(List<EntityTuple> nodes, List<TraitValue> nodeLocations) {
        this.nodes = Lists.newArrayList(nodes);
        this.locations = Lists.newArrayList(nodeLocations);
    }

    public NodesWithLocations() {
        this.nodes = Lists.newArrayList();
        this.locations = Lists.newArrayList();
    }

    public List<EntityTuple> getNodes() {
        return nodes;
    }

    public ASTNode findParentLocation(ASTNode node, EntityDataBase edb) {
        for (TraitValue location : locations) {
            List<EntityTuple> entities = location.getEntities();
            for (EntityTuple tuple : entities) {
                Object entity = tuple.lookupEntity(EntityDataBase.CHILD_VAR_REF);
                if (entity == node) {
                    Object parent = tuple.lookupEntity(EntityDataBase.PARENT_VAR_REF);
                    if (parent instanceof ITypeBinding) {
                        parent = edb.lookupTypeDeclaration((ITypeBinding)parent);
                    }
                    return (ASTNode)parent;
                }
            }
        }
        return null;
    }

    public void merge(NodesWithLocations that) {
        this.nodes.addAll(that.nodes);
        toAdd: for (TraitValue trait : that.locations) {
            TraitSignature type = trait.getTraitType();
            String name = type.getName();

            for (TraitValue builtInAlreadyPresent : this.locations) {
                if (type.equals(builtInAlreadyPresent.getTraitType())) {
                    for (EntityTuple entityTuple : trait.getEntities()) {
                        builtInAlreadyPresent.addTuple(entityTuple);
                    }
                    continue toAdd;
                }
            }
            this.locations.add(trait);
        }
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        String spacer = String.format("%n  ");
        buff.append("Nodes:");
        buff.append(spacer);
        buff.append(StringUtil.separate(nodes, spacer));
        buff.append(String.format("%nLocations:"));
        buff.append(spacer);
        buff.append(StringUtil.separate(locations, spacer));
        return buff.toString();
    }

    public List<TraitValue> getLocations() {
        return locations;
    }
}