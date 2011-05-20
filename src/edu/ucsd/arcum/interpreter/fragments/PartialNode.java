package edu.ucsd.arcum.interpreter.fragments;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.*;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.interpreter.transformation.Conversion;

public class PartialNode extends ProgramFragment
{
    private Class rootType;
    private Map<StructuralPropertyDescriptor, ProgramFragment> children;
    private ArrayList<StructuralPropertyDescriptor> iterationOrder;

    public PartialNode(Class rootType) {
        this.rootType = rootType;
        this.children = new IdentityHashMap<StructuralPropertyDescriptor, ProgramFragment>(
            5);
        this.iterationOrder = Lists.newArrayList();
    }

    public void addBranch(StructuralPropertyDescriptor edge,
        ProgramFragment branch)
    {
        children.put(edge, branch);
        iterationOrder.add(edge);
    }

    // Removing a branch means that its value won't be taken into consideration
    // during matching
    public void removeBranch(StructuralPropertyDescriptor spd) {
        children.remove(spd);
        iterationOrder.remove(spd);
    }

    @Override protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append("(");
        buff.append(rootType.getSimpleName());

        getIndenter().indent();
        for (Map.Entry<StructuralPropertyDescriptor, ProgramFragment> child : children
            .entrySet())
        {
            buff.append(getIndenter().newLine());
            buff.append("<");
            buff.append(child.getKey().getId());
            buff.append(String.format(">%n"));
            ProgramFragment value = child.getValue();
            if (value != null) {
                value.buildString(buff);
            }
            else {
                buff.append(getIndenter());
                buff.append("<<null>>");
            }
        }
        buff.append(")");
        getIndenter().unindent();
    }

    @Override public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        ASTNode node = ast.createInstance(rootType);
        BindingMap result = bindRoot(node);

        for (StructuralPropertyDescriptor spd : iterationOrder) {
            ProgramFragment childPattern = children.get(spd);

            BindingMap child = childPattern.generateNode(lookup, ast);
            result = result.consistentMerge(child);
            Object entity = child.getResult();
            if (spd.isChildListProperty()) {
                List structuralASTList = (List)node.getStructuralProperty(spd);
                EntityList entityList = (EntityList)entity;
                int numMembers = entityList.size();
                for (int i = 0; i < numMembers; ++i) {
                    Object listMember = entityList.getEntity(i);
                    ProgramFragment fragment = entityList.getFragment(i);
                    ASTNode n = Conversion.toPossibleEmptyNode(ast, spd, listMember);
                    if (n != null) {
                        structuralASTList.add(n);
                        result.boundValueUpdated(listMember, n);
                    }
                }
            }
            else if (spd.isChildProperty()) {
                Object originalValue = entity;
                entity = Conversion.toPossibleEmptyNode(ast, spd, entity);
                if (entity != null) {
                    entity = Conversion.unbox(ast, spd, (ASTNode)entity);
                    node.setStructuralProperty(spd, entity);
                    result.boundValueUpdated(originalValue, entity);
                }
            }
            else {
                entity = Conversion.toSimpleProperty(entity);
                node.setStructuralProperty(spd, entity);
            }
        }
        return result;
    }

    public Class getRootType() {
        return rootType;
    }

    @Override protected BindingMap matchesASTNode(ASTNode node) {
        if (rootType.isInstance(node)) {
            Set<Entry<StructuralPropertyDescriptor, ProgramFragment>> entrySet;
            Iterator<Entry<StructuralPropertyDescriptor, ProgramFragment>> it;
            Entry<StructuralPropertyDescriptor, ProgramFragment> entry;

            BindingMap result = bindRoot(node);

            entrySet = children.entrySet();
            it = entrySet.iterator();
            while (it.hasNext()) {
                entry = it.next();
                StructuralPropertyDescriptor spd = entry.getKey();
                ProgramFragment childPattern = entry.getValue();

                @Union("Entity") Object childEntity = node.getStructuralProperty(spd);
                if (spd.isChildListProperty()) {
                    SubtreeList listOfPatterns = (SubtreeList)childPattern;
                    List childList = (List)childEntity;
                    EntityList entityList = new EntityList(spd);
                    int numChildren = childList.size();
                    for (int i = 0; i < numChildren; ++i) {
                        Object childListElement = childList.get(i);
                        if (numChildren == listOfPatterns.size()) {
                            entityList.addEntity(childListElement, listOfPatterns.get(i));
                        }
                        else {
                            entityList.addEntity(childListElement, null);
                        }
                    }
                    childEntity = entityList;
                }
                if (childEntity == null) {
                    childEntity = new EmptyEntityInfo(node, spd);
                }
                BindingMap theta = childPattern.matches(childEntity);
                if (theta == null) {
                    return null;
                }
                result.addBindings(theta);
            }
            if (DEBUG) {
                System.out.printf("Returning a match:%n[%s]%nwith:%n[%s]%n", this, node);
            }
            return result;
        }
        else {
            return null;
        }
    }

    @Override protected BindingMap matchesSignatureEntity(SignatureEntity signature) {
        return matchesASTNode(signature.getSignatureNode());
    }

    public ProgramFragment lookupEdge(StructuralPropertyDescriptor edge) {
        return children.get(edge);
    }
}