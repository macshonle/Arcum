package edu.ucsd.arcum.interpreter.fragments;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.query.Entity;

public class EntityList implements ISynthesizedEntity
{
    private List<Object> entities;
    private List<ProgramFragment> fragments;
//DELETEME    private StructuralPropertyDescriptor locationInParent;
    private boolean isModifiersList;

    public static EntityList newModifiersList() {
        EntityList result = new EntityList();
        result.isModifiersList = true;
        return result;
    }
    
    private EntityList() {
        this.entities = Lists.newArrayList();
        this.fragments = Lists.newArrayList();        
    }
    
    public EntityList(StructuralPropertyDescriptor locationInParent) {
        this();
//DELETEME        this.locationInParent = locationInParent;
        this.isModifiersList = Entity.isModifiersEdge(locationInParent);
    }

    // The given entity must either be an ASTNode, an ITypeBinding, or a
    // ModifierElement. If no fragment is bound to entity (as when generating the
    // database) then null may be passed for "fragment".
    public void addEntity(Object entity, ProgramFragment fragment) {
        if (entity instanceof ASTNode || entity instanceof ITypeBinding
            || entity instanceof ModifierElement)
        {
            entities.add(entity);
            fragments.add(fragment);
        }
        else {
            ArcumError.fatalError("Bad entity type given to EntityList");
        }
    }

    public boolean isEmpty() {
        return entities.isEmpty();
    }

    public int size() {
        return entities.size();
    }

    public Object getEntity(int index) {
        return entities.get(index);
    }

    public ProgramFragment getFragment(int index) {
        return fragments.get(index);
    }

    public List getList() {
        return entities;
    }

    public boolean isModifiersList() {
        return isModifiersList;
    }

    // DELETEME
//    public StructuralPropertyDescriptor getLocationInParent() {
//        return locationInParent;
//    }
}
