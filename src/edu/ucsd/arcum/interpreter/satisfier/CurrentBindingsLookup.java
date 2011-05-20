package edu.ucsd.arcum.interpreter.satisfier;

import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;

import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.fragments.Union;
import edu.ucsd.arcum.interpreter.parser.FragmentParser;
import edu.ucsd.arcum.interpreter.query.EntityTuple;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;

public class CurrentBindingsLookup implements IEntityLookup
{
    private final IEntityLookup base;
    private final BindingMap bindingMap;

    public CurrentBindingsLookup(IEntityLookup base, BindingMap bindingMap) {
        this.base = base;
        this.bindingMap = bindingMap;
    }

    public CurrentBindingsLookup(IEntityLookup base, EntityTuple entity) {
        this.base = base;
        this.bindingMap = new BindingMap(entity.getValues(), entity.getTypes());
    }

    @Override public Object lookupEntity(String reference) {
        @Union("Entity") Object entity = bindingMap.lookupEntity(reference);
        if (entity == null) {
            entity = base.lookupEntity(reference);
        }
        return entity;
    }

    @Override public String lookupEntitiesID(Object entity) {
        String id = bindingMap.lookupEntitiesID(entity);
        if (id == null) {
            id = base.lookupEntitiesID(entity);
        }
        return id;
    }
    
    @Override public BindingMap extractAsBindings() {
        BindingMap theta = base.extractAsBindings();
//        theta.addBindings(bindingMap); MACNEIL: replaced with consistentMerge 
        return theta.consistentMerge(bindingMap);
//        return theta;
    }

    @Override public FormalParameter findResolvedSingleton(String variableName) {
        return base.findResolvedSingleton(variableName);
    }

    @Override public FragmentParser newParser(boolean matchingMode) {
        return base.newParser(matchingMode);
    }

    @Override public ITypeBinding lookupTypeBinding(ASTNode node) {
        return base.lookupTypeBinding(node);
    }

    @Override public AbstractTypeDeclaration lookupTypeDeclaration(
        ITypeBinding givenBinding)
    {
        return base.lookupTypeDeclaration(givenBinding);
    }

    @Override
    public TypeLookupTable getTypeLookupTable() {
        TypeLookupTable result = base.getTypeLookupTable();
        Map<String, EntityType> types = bindingMap.getTypes();
        for (Entry<String, EntityType> entry : types.entrySet()) {
            result.addType(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
