package edu.ucsd.arcum.interpreter.fragments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Modifier;

import com.google.common.collect.Lists;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.query.EntityType;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.util.StringUtil;

public class SubtreeList extends ProgramFragment
{
    public enum Kind
    {
        ORDER_MATTERS, UNORDERED
    };

    private final List<ProgramFragment> nodes;
    private final Kind kind;

    public SubtreeList(List<? extends ProgramFragment> nodes, Kind kind) {
        this.nodes = Lists.newArrayList(nodes);
        this.kind = kind;
    }

    public ProgramFragment get(int index) {
        return nodes.get(index);
    }

    // Creates a new independent list, but it will point to the same program
    // fragments and not copies of them
    public SubtreeList(SubtreeList that) {
        this.nodes = Lists.newArrayList(that.nodes);
        this.kind = that.kind;
    }

    // Adds the given ProgramFragment to each element in the list. If the
    // given list is empty a new SubtreeList will be created with the given type and
    // kind and added to the list.
    public static void addToAll(List<SubtreeList> list, ProgramFragment toAdd,
        Kind kind)
    {
        if (list.isEmpty()) {
            SubtreeList subtreeList;
            subtreeList = new SubtreeList(Lists.newArrayList(toAdd), kind);
            list.add(subtreeList);
        }
        else {
            for (SubtreeList subtreeList : list) {
                subtreeList.nodes.add(toAdd);
            }
        }
    }

    // Creates copies of all of the subtree lists in "list" and adds the given
    // ProgramFragment to each. If the given list is empty two new SubtreeLists
    // will be created with the given type and kind and returned in the collection, but
    // only one of the lists will have the element to be added.
    public static List<SubtreeList> copyAllAndAdd(Collection<SubtreeList> list,
        ProgramFragment toAdd, SubtreeList.Kind kind)
    {
        List<SubtreeList> result = new ArrayList<SubtreeList>();
        if (list.isEmpty()) {
            SubtreeList subtreeList;
            subtreeList = new SubtreeList(new ArrayList<ProgramFragment>(), kind);
            result.add(subtreeList);
            subtreeList = new SubtreeList(Lists.newArrayList(toAdd), kind);
            result.add(subtreeList);
        }
        else {
            for (SubtreeList original : list) {
                SubtreeList copyOfOriginal = new SubtreeList(original);
                result.add(copyOfOriginal);
            }
            for (SubtreeList original : list) {
                SubtreeList originalWithToAdd = new SubtreeList(original);
                originalWithToAdd.nodes.add(toAdd);
                result.add(originalWithToAdd);
            }
        }
        return result;
    }

    @Override protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append("(SubtreeList");
        if (!nodes.isEmpty()) {
            buff.append(String.format("%n"));
            getIndenter().indent();
            Iterator<ProgramFragment> it = nodes.iterator();
            while (it.hasNext()) {
                ProgramFragment node = it.next();
                node.buildString(buff);
                if (it.hasNext()) {
                    buff.append(String.format("%n"));
                }
            }
        }
        buff.append(")");
        if (!nodes.isEmpty()) {
            getIndenter().unindent();
        }
    }

//    @Override
//    public Object instantiateNew(AST ast, IEntityLookup entities,
//            Map<String, String> importCouplings) {
//        List result = new ArrayList<Object>();
//        for (ProgramFragment node: nodes) {
//            Object instance = node.instantiateNew(ast, entities, importCouplings);
//            result.add(instance);
//        }
//        return result;
//    }

    @Override public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        EntityList list = new EntityList(null);
        BindingMap result = bindRoot(list);
        for (ProgramFragment node: nodes) {
            BindingMap child = node.generateNode(lookup, ast);
            Object entity = child.getResult();
            list.addEntity(entity, node);
            result = result.consistentMerge(child);
        }
        return result;
    }
    
    @Override protected BindingMap matchesEntityList(EntityList value) {
        List astNodes = value.getList();
        if (kind == Kind.ORDER_MATTERS) {
            return orderedComparison(nodes, astNodes, value);
        }
        else if (kind == Kind.UNORDERED) {
            return unorderedComparison(nodes, astNodes, value);
        }
        else {
            ArcumError.fatalError("Impossible case");
            return null;
        }
    }

    private BindingMap orderedComparison(List<ProgramFragment> fragments,
        List astNodes, EntityList originalRoot)
    {
        if (astNodes.size() != fragments.size()) {
            return null;
        }
        BindingMap result = bindRoot(originalRoot);
        Iterator<ProgramFragment> i = fragments.iterator();
        Iterator<?> j = astNodes.iterator();
        while (i.hasNext()) {
            ProgramFragment childPattern = i.next();
            Object child = j.next();
            BindingMap theta = childPattern.matches(child);
            if (theta == null) {
                return null;
            }
            result.addBindings(theta);
        }
        return result;
    }

    private BindingMap unorderedComparison(List<ProgramFragment> fragments,
        List astNodes, EntityList originalRoot)
    {
        astNodes = Lists.newArrayList(astNodes);
        fragments = Lists.newArrayList(fragments);

//DELETEME      if (EntityList.isModifiersEdge(originalRoot.getLocationInParent())) {
      if (originalRoot.isModifiersList()) {
            return compareModifiers(fragments, astNodes, originalRoot);
        }
        else {
            if (astNodes.size() != fragments.size()) {
                return null;
            }
            
            BindingMap result = bindRoot(originalRoot);

            fragmentSearch: for (ProgramFragment fragment : fragments) {
                Iterator<Object> astIt = astNodes.iterator();
                while (astIt.hasNext()) {
                    Object astNode = astIt.next();
                    BindingMap theta = fragment.matches(astNode);
                    if (theta != null) {
                        result.addBindings(theta);
                        astIt.remove();
                        continue fragmentSearch;
                    }
                }
                // if we get to here the fragment has no match, the lists aren't the same
                return null;
            }
            return result;
        }
    }

    private BindingMap compareModifiers(List<ProgramFragment> fragments,
        List astNodes, EntityList originalRoot)
    {
        BindingMap result = bindRoot(originalRoot);
        
        ModifierElement accessSpecifier = removeAccessSpecifier(astNodes);
        
        BindingMap theta = matchAndRemoveAccessSpecifier(accessSpecifier, fragments);
        if (theta != null) {
            result.addBindings(theta);
        }
        else {
            // no chance for a match
            return null;
        }
        
        if (fragments.size() != astNodes.size()) {
            return null;
        }
        
        ArrayList<ProgramFragment> fragmentsNoVariables;
        fragmentsNoVariables = Lists.newArrayList(fragments);
        
        ArrayList<ProgramFragment> onlyVariables;
        onlyVariables = Lists.newArrayList();
        
        {
            Iterator<ProgramFragment> it = fragmentsNoVariables.iterator();
            while (it.hasNext()) {
                ProgramFragment fragment = it.next();
                if (fragment instanceof VariableNode) {
                    it.remove();
                    onlyVariables.add(fragment);
                }
            }
        }
        
        fragmentSearch: for (ProgramFragment fragment : fragmentsNoVariables) {
            Iterator<Object> astIt = astNodes.iterator();
            while (astIt.hasNext()) {
                Object astNode = astIt.next();
                theta = fragment.matches(astNode);
                if (theta != null) {
                    result.addBindings(theta);
                    astIt.remove();
                    continue fragmentSearch;
                }
            }
            // if we get to here the fragment has no match, the lists aren't the same
            return null;
        }
        
        if (onlyVariables.size() > 1) {
            ArcumError.fatalUserError(SourceLocation.GENERATED,
                "Unsupported: Multiple variables matching non-access specifier: %s",
                StringUtil.separate(onlyVariables));
        }
        
        if (onlyVariables.size() != astNodes.size()) {
            return null;
        }
        
        if (onlyVariables.size() == 1) {
            ProgramFragment fragment = onlyVariables.get(0);
            theta = fragment.matches(astNodes.get(0));
            if (theta != null) {
                result.addBindings(theta);
            }
            else {
                return null;
            }
        }
        return result;
    }

    private boolean isListOfModifiers(List astNodes) {
        return true;
    }

    // Removes the optional access specifier from the given list of astNodes (it is
    // assumed that at most one access specifier is present in the list), returning
    // the ModifierElement translated value of the specifier. Returns the special
    // package specifier if neither public/private/protected are present.
    private ModifierElement removeAccessSpecifier(List astNodes) {
        ModifierElement result = ModifierElement.MOD_PACKAGE;
        for (Iterator it = astNodes.iterator(); it.hasNext();) {
            Object astNode = it.next();
            if (astNode instanceof Modifier) {
                Modifier modifier = (Modifier)astNode;
                ModifierElement modifierElement = ModifierElement.lookup(modifier);
                if (modifierElement != null && modifierElement.isAccessSpecifier()) {
                    result = modifierElement;
                    it.remove();
                }
            }
            else {
                ArcumError.fatalError("Unimplemented: An unordered list that"
                    + " isn't for modifiers");
            }
        }
        return result;
    }

    private BindingMap matchAndRemoveAccessSpecifier(ModifierElement accessSpecifier,
        List<ProgramFragment> fragments)
    {
        BindingMap theta = BindingMap.newEmptyMap();
        for (Iterator<ProgramFragment> it = fragments.iterator(); it.hasNext();) {
            ProgramFragment fragment = it.next();
            if (fragment instanceof VariableNode) {
                VariableNode variableNode = (VariableNode)fragment;
                EntityType type = variableNode.getNodeType();
                if (type.isAssignableFrom(EntityType.ACCESS_SPECIFIER)) {
                    it.remove();
                    theta = variableNode.matches(accessSpecifier);
                }
            }
            else if (fragment instanceof ResolvedEntity) {
                ResolvedEntity resolvedEntity = (ResolvedEntity)fragment;
                Object value = resolvedEntity.getValue();
                if (value instanceof ModifierElement) {
                    ModifierElement modifierElement = (ModifierElement)value;
                    if (accessSpecifier.equals(modifierElement)) {
                        it.remove();
                        theta = resolvedEntity.matches(accessSpecifier);
                    }
                    else if (modifierElement.isAccessSpecifier()) {
                        return null;
                    }
                }
            }
        }
        return theta;
    }

    public int size() {
        return nodes.size();
    }
}