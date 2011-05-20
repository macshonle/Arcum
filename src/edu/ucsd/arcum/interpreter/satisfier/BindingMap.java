package edu.ucsd.arcum.interpreter.satisfier;

import static com.google.common.collect.Lists.transform;
import static com.google.common.collect.Maps.newHashMapWithExpectedSize;
import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.*;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.fragments.Union;
import edu.ucsd.arcum.interpreter.fragments.VariableNode;
import edu.ucsd.arcum.interpreter.query.*;
import edu.ucsd.arcum.util.Pure;
import edu.ucsd.arcum.util.StringUtil;

public class BindingMap implements Comparable<BindingMap>
{
    private static final String SPECIAL_RESULT_LABEL = "<RESULT>";

    // EXAMPLE: Issue: we can't label the Object as an Entity in the generic arg
    // could type inference come to the aid? Or labeling type args?
    private final @Union("Entity") SortedMap<String, Object> bindings;
    private final SortedMap<String, EntityType> types;

    public static BindingMap newEmptyMap() {
        return new BindingMap();
    }

    private BindingMap() {
        this.bindings = new TreeMap<String, Object>();
        this.types = new TreeMap<String, EntityType>();
    }

    public BindingMap(Map<String, Object> values, Map<String, EntityType> types) {
        this();
        for (Entry<String, Object> entry : values.entrySet()) {
            String var = entry.getKey();
            Object entity = entry.getValue();
            bind(var, entity, types.get(var));
        }
    }

    public BindingMap(Object specialResult) {
        this();
        bind(SPECIAL_RESULT_LABEL, specialResult, EntityType.ANY);
    }

    public void bind(String id, @Union("Entity") Object entity, EntityType type) {
        if (!id.equals(VariableNode.DONT_CARE)) {
            bindings.put(id, entity);
            types.put(id, type);
        }
    }

    public void bindResultAs(String name) {
        @Union("Entity") Object entity = bindings.get(SPECIAL_RESULT_LABEL);
//        bindings.remove(SPECIAL_RESULT_LABEL);
        bindings.put(name, entity);
    }

    public Object getResult() {
        return bindings.get(SPECIAL_RESULT_LABEL);
    }

    // MACNEIL: Should some calls to this actually be calls to consistentMerge?
    public void addBindings(BindingMap theta) {
        for (Entry<String, Object> entry : theta.bindings.entrySet()) {
            String newKey = entry.getKey();
            Object newValue = entry.getValue();
            if (isTempName(newKey)) {
                continue;
            }
            if (this.bindings.containsKey(newKey)) {
                ArcumError.fatalError("Variable \"%s\" is multiple-bound", newKey);
            }
            bindings.put(newKey, newValue);
        }
    }

    private boolean isTempName(String key) {
        return key.equals(SPECIAL_RESULT_LABEL);
    }

    @Pure public BindingMap consistentMerge(BindingMap... maps) {
        BindingMap result = this;
        for (BindingMap map : maps) {
            result = result.consistentMerge(map);
        }
        return result;
    }

    // Returns null if a consistent merge cannot be accomplished (i.e., they share
    // variables that do not have equal values). When the values of a specific
    // variable in the bindings have the same name and the same value the value of
    // the result will be what was found in "that".
    @Pure public BindingMap consistentMerge(BindingMap that) {
        BindingMap result = new BindingMap();
        for (Entry<String, Object> thisEntry : this.bindings.entrySet()) {
            String thisID = thisEntry.getKey();
            @Union("Entity") Object thisEntity = thisEntry.getValue();
            if (!isTempName(thisID) && that.bindings.containsKey(thisID)) {
                @Union("Entity") Object thatEntity = that.bindings.get(thisID);
                if (Entity.compareTo(thisEntity, thatEntity) == 0) {
                    // Even though thatEntity is equivalent to thisEntity we keep
                    // the newer one from that
                    result.bind(thisID, thatEntity, that.types.get(thisID));
                }
                else {
                    // the two bindings can never be consistent
                    return null;
                }
            }
            else {
                result.bind(thisID, thisEntity, this.types.get(thisID));
            }
        }
        for (Entry<String, Object> thatEntry : that.bindings.entrySet()) {
            String thatID = thatEntry.getKey();
            if (isTempName(thatID))
                continue;
            @Union("Entity") Object thatEntity = thatEntry.getValue();
            if (!result.bindings.containsKey(thatID)) {
                // add it only if we haven't already
                result.bind(thatID, thatEntity, that.types.get(thatID));
            }
        }
        return result;
    }

    // Returns a copy of the binding map, but with all bindings associated with
    // the given variables removed
    @Pure public BindingMap withVarsRemoved(Set<String> varNames) {
        BindingMap result = BindingMap.newEmptyMap();
        for (Entry<String, Object> entry : this.bindings.entrySet()) {
            String id = entry.getKey();
            Object entity = entry.getValue();

            if (!varNames.contains(id)) {
                result.bind(id, entity, this.types.get(id));
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    @Override public String toString() {
        StringBuilder buff = new StringBuilder();
        buff.append("(");
        Iterator<Entry<String, Object>> i = bindings.entrySet().iterator();
        while (i.hasNext()) {
            Entry<String, Object> entry = i.next();
            String id = entry.getKey();
            @Union("Entity") Object entity = entry.getValue();
            buff.append(id);
            buff.append(String.format(" [%x]: ", System.identityHashCode(entity)));
            buff.append(Entity.valueAsString(entity));
            if (i.hasNext()) {
                buff.append(String.format(",%n "));
            }
        }
        buff.append("): ");
        buff.append(this.bindings.size());
        return buff.toString();
    }

    @Override public int compareTo(BindingMap that) {
        // TUESDAY: (!!!) This maybe should not be used at all. Trace where this is
        // called. Maybe only trait values should be comparable like this. As a result,
        // we could have binding maps share their representations by being a linked
        // structure: The links won't be too deep because it's bound by the number
        // of variables used in the expression, with globals serving as the base
        // binding map. It'd be nice for BindingMap instances to be immutable as well.
        if (this == that) {
            return 0;
        }
        else {
            int thisSize = this.bindings.size();
            int thatSize = that.bindings.size();
            if (thisSize != thatSize) {
                return thisSize - thatSize;
            }
            Iterator<String> i = this.bindings.keySet().iterator();
            Iterator<String> j = that.bindings.keySet().iterator();
            while (i.hasNext()) {
                String thisID = i.next();
                String thatID = j.next();
                if (!thisID.equals(thatID)) {
                    return thisID.compareTo(thatID);
                }
                else {
                    @Union("Entity") Object thisEntity = this.bindings.get(thisID);
                    @Union("Entity") Object thatEntity = that.bindings.get(thisID);
                    int k = Entity.compareToWithLocations(thisEntity, thatEntity);
                    if (k != 0) {
                        return k;
                    }
                }
            }
            return 0;
        }
    }

    public List<EntityTuple> asEntityTuple(List<TraitSignature> types) {
        List<EntityTuple> result = new ArrayList<EntityTuple>();
        for (TraitSignature type : types) {
//            if (nodeValue == null) {
//                // XXX (!!!) -- Could also apply this check to all entities in
//                // this.bindings and update them to a different Entity type (though
//                // that may get weird under generics, so we could punt on all uses
//                // of parameterized types in a non-raw form)
//                ITypeBinding typeBinding = Entity.getTypeBindingValue(root);
//                AbstractTypeDeclaration decl = edb.lookupTypeDeclaration(typeBinding);
//                // nodeValue may still be null
//                nodeValue = decl;
//            }
            List<FormalParameter> formals = type.getFormals();
            List<String> vars = transform(type.getFormals(),
                FormalParameter.getIdentifier);
            int numMembers = formals.size();
            List<ASTNode> entities = Lists.newArrayListWithExpectedSize(numMembers);
            Map<String, Object> tupleSubValues = newHashMapWithExpectedSize(numMembers);
            for (String var : vars) {
                Object entity = bindings.get(var);
                tupleSubValues.put(var, entity);
                if (entity instanceof ASTNode) {
                    entities.add((ASTNode)entity);
                }
                // TASK !!!!
//                else if (entity instanceof ITypeBinding) {
//                    AbstractTypeDeclaration atd;
//                    atd = EntityDataBase.findTypeDeclaration((ITypeBinding)entity);
//                    if (atd != null) {
//                        tupleSubValues.put(var, atd); // overwrite previous entry
//                        entities.add(atd);
//                    }
//                }
                else {
                    // MACNEIL: Might want to ASTNodeify typebindings, like
                    // in the commented out code above
                    // MACNEIL: Could maybe do something about pseudo-parents here
                    // too
                }
            }
            ASTNode root = findRoot(entities);
            EntityTuple tuple = new EntityTuple(type, tupleSubValues, root);
            result.add(tuple);
        }
        return result;
    }

    // Finds the common parent of all of the astNodes, or returns null if no such
    // parent exists. NOTE: Similar but not quite the same as findTrees in ASTUtil.
    private ASTNode findRoot(List<ASTNode> astNodes) {
        if (astNodes.isEmpty()) {
            return null;
        }
        if (astNodes.size() == 1) {
            return astNodes.get(0);
        }
        Iterator<ASTNode> it = astNodes.iterator();
        ASTNode node = it.next();
        AST ast = node.getAST();
        while (it.hasNext()) {
            node = it.next();
            if (!ast.equals(node.getAST())) {
                return null;
            }
        }
        List<ASTNode> candidates = Lists.newArrayList(astNodes);
        it = candidates.iterator();
        nextElement: while (it.hasNext()) {
            ASTNode curElement = it.next();
            ASTNode expecting = curElement;
            ASTNode previous = curElement;
            while (expecting != null) {
                previous = expecting;
                expecting = expecting.getParent();
                if (astNodes.contains(expecting)) {
                    // it can't be us, because another node is our parent
                    it.remove();
                    continue nextElement;
                }
            }
            if (expecting == null && previous instanceof CompilationUnit) {
                // we made it to the top without finding our parent, so we must
                // be the root. The check for CompilationUnit avoids desugared nodes
                // that are implicit (like the "this." that gets inserted) from
                // acting like the top, just because their parents are null too
                return curElement;
            }
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        else {
            return null;
        }
    }

    public Object lookupEntity(String name) {
        return bindings.get(name);
    }

    public String lookupEntitiesID(Object entity) {
        entrySearch: for (Entry<String, Object> entry : bindings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key.equals(SPECIAL_RESULT_LABEL))
                continue entrySearch;
            if (value == entity) {
                return key;
            }
        }
        return null;
    }

    public List<TraitValue> extractBuiltInTraits() {
        List<TraitValue> result = Lists.newArrayList();
        for (Entry<String, Object> entry : bindings.entrySet()) {
            String traitName = entry.getKey();
            if (EntityDataBase.isBuiltInTrait(traitName)) {
                Object value = entry.getValue();
                result.add((TraitValue)value);
            }
        }
        return result;
    }

    public void boundValueUpdated(Object originalValue, Object newValue) {
        Set<String> keys = Sets.newHashSet(bindings.keySet());
        for (String key : keys) {
            Object value = bindings.get(key);
            if (value == originalValue) {
                if (DEBUG) {
                    System.out.printf("updated %s%n ----to %s%n", StringUtil
                        .debugDisplay(originalValue), StringUtil.debugDisplay(newValue));
                    System.out.flush();
                }
                bindings.put(key, newValue);
            }
        }
    }

    public Map<String,EntityType> getTypes() {
        return types;
    }
}