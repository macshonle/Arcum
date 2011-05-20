package edu.ucsd.arcum.interpreter.query;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.*;

import org.eclipse.jdt.core.dom.ASTNode;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.ast.FormalParameter;
import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.fragments.ASTNodeReplacer;
import edu.ucsd.arcum.interpreter.fragments.Union;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;
import edu.ucsd.arcum.util.Accessor;

public class EntityTuple implements Comparable<EntityTuple>
{
    public static Function<EntityTuple, ASTNode> getRootNode = Accessor.getFunction(
        EntityTuple.class, ASTNode.class, "getRootNode");
    
    private TraitSignature type;
    private @Union("Entity") SortedMap<String, Object> values;
    private ASTNode rootNode;

    // if rootNode is null then the EntityTuple was probably matched via
    // given parameters and can thus be without a root
    public EntityTuple(TraitSignature type, Map<String, Object> values, ASTNode rootNode)
    {
        this.type = type;
        this.values = new TreeMap<String, Object>(values);
        this.rootNode = rootNode;
//        toString(); FIXME: uncomment this to reveal hidden bugs
    }

    // returns the name and types of the variables bound by this tuple
    public Set<FormalParameter> getBoundVariables() {
        return new HashSet<FormalParameter>(type.getFormals());
    }

    public @Union("Entity") Object lookupEntity(String variable) {
        return values.get(variable);
    }

    @Override public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(type.getName());
        builder.append("<");
        appendTupleMembers(builder);
        builder.append(">");
        return builder.toString();
    }

    public String toDisplayOfMembersString() {
        StringBuilder builder = new StringBuilder();
        appendTupleMembers(builder);
        return builder.toString();
    }
    
    private void appendTupleMembers(StringBuilder builder) {
        Iterator<FormalParameter> iterator = type.getFormals().iterator();
        while (iterator.hasNext()) {
            FormalParameter formal = iterator.next();
            String id = formal.getIdentifier();
            builder.append(id);
            builder.append(":");
            @Union("Entity") Object entity = values.get(id);
            builder.append(Entity.getDisplayString(entity));
//            if (DEBUG){
//                builder.append(" ");
//                builder.append(StringUtil.debugDisplay(entity));
//            }
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
    }

    public TraitSignature getType() {
        return type;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    // may return null
    public ASTNode getRootNode() {
        return rootNode;
    }
    
    // Return the first element if it's an ASTNode, otherwise returns the so-called
    // root node.
    public ASTNode getMainDisplayNode() {
        String firstID = type.getFormals().get(0).getIdentifier();
        Object value = values.get(firstID);
        if (value instanceof ASTNode) {
            return (ASTNode)value;
        }
        else {
            return getRootNode();
        }
    }

    @Override public int compareTo(EntityTuple that) {
        int n = this.type.getName().compareTo(that.type.getName());
        if (n == 0) {
            Iterator<Object> i = this.values.values().iterator();
            Iterator<Object> j = that.values.values().iterator();
            while (i.hasNext()) {
                @Union("Entity") Object e = i.next();
                @Union("Entity") Object f = j.next();
                int k = Entity.compareToWithLocations(e, f);
                if (k != 0) {
                    return k;
                }
            }
        }
        return n;
    }

    // An arg in the "args" list is either a program entity or a VariablePlaceholder.
    // Arguments are assumed to be in order. Returns a BindingMap (potentially empty)
    // if there is a match; otherwise there is no match and null is returned.
    public BindingMap matches(List<FormalParameter> formals, List<Object> args) {
        BindingMap result = BindingMap.newEmptyMap();
        int size = formals.size();
        argChecking: for (int i = 0; i < size; ++i) {
            Object arg = args.get(i);
            FormalParameter formal = formals.get(i);
            Object foundEntity = values.get(formal.getIdentifier());
            // TASK: use a different kind of entity instead
            if (arg instanceof VariablePlaceholder) {
                VariablePlaceholder variable = (VariablePlaceholder)arg;
                if (variable.isSpecialAnyVariable()) {
                    continue argChecking;
                }
                else {
                    String name = variable.getName();
                    // GETDONE: Always binding, because the Modifiers, Signature cases
                    // need to be rethought anyway. What we should be doing instead is
                    // statically type checking the use of relations and the variables
                    // related to them... currently, we could get some weird stuff, like
                    // binding a class to an expression variable
                    result.bind(name, foundEntity, formal.getType());
//                    EntityType entityType = variable.getType();
//                    if (entityType.isInstance(foundEntity)) {
//                        result.bind(name, foundEntity);
//                    }
//                    else {
//                        if (DEBUG) {
//                            System.out.printf("Filtering out %s because it isn't a %s%n",
//                                Entity.getDisplayString(foundEntity), entityType);
//                        }
//                        return null;
//                    }
                }
            }
            else {
                if (Entity.compareToWithLocations(arg, foundEntity) != 0) {
                    return null;
                }
            }
        }
        return result;
    }

//    public void updateDescendants(Map<ASTNode, ASTNode> updatedNodeLookup) {
////        ASTTraverseTable table = new ASTTraverseTable();
////        IASTVisitor traverser = new NodeUpdater(updatedNodeLookup);
////        table.traverseAST(rootNode, traverser);
//        ASTNode originalRootNode = rootNode;
//        this.rootNode = updateDescendantsOfNode(rootNode, updatedNodeLookup);
//        ASTUtil.recordSugaredNode(originalRootNode, rootNode);
//        
//        updatedNodeLookup = Maps.newHashMap(updatedNodeLookup);
//        updatedNodeLookup.put(originalRootNode, rootNode);
//        Set<String> members = Sets.newHashSet(values.keySet());
//        for (String key : members) {
//            Object object = values.get(key);
//            if (updatedNodeLookup.containsKey(object)) {
//                values.put(key, updatedNodeLookup.get(object));
//            }
//        }
//    }
//
//    private ASTNode updateDescendantsOfNode(ASTNode node, Map<ASTNode, ASTNode> lookup) {
//        if (lookup.containsKey(node)) {
//            return lookup.get(node);
//        }
//        else if (node instanceof QualifiedName) {
//            QualifiedName qname = (QualifiedName)node;
//            Name qualifier = qname.getQualifier();
//            if (lookup.containsKey(qualifier)) {
//                AST ast = node.getAST();
//                FieldAccess fieldAccess = ast.newFieldAccess();
//                Expression expression = (Expression)lookup.get(qualifier);
//                expression = (Expression)Conversion.cleanseASTNode(ast, expression);
//                fieldAccess.setExpression(expression);
//                SimpleName name = (SimpleName)Conversion.cleanseASTNode(ast, qname.getName());
//                fieldAccess.setName(name);
//                ASTUtil.recordSugaredNode(node, fieldAccess);
//                return fieldAccess;
//            }
//            return node;
//        }
//        if (alwaysTrue())
//            return node;
//        else {
//            StructuralPropertyDescriptor[] spds = ASTTraverseTable.getProperties(node);
//            edges: for (StructuralPropertyDescriptor spd : spds) {
//                Object property = node.getStructuralProperty(spd);
//                if (property == null) {
//                    continue edges;
//                }
//                if (spd.isChildProperty()) {
//                    ASTNode n = updateDescendantsOfNode((ASTNode)property, lookup);
//                    if (n != null) {
//                        node.setStructuralProperty(spd, n);
//                    }
//                }
//                else if (spd.isChildListProperty()) {
//                    List children = (List)property;
//                    for (int i = 0; i < children.size(); ++i) {
//                        ASTNode n = updateDescendantsOfNode((ASTNode)children.get(i), lookup);
//                        if (n != null) {
//                            children.set(i, n);
//                        }
//                    }
//                }
//            }
//        }
//        return ASTNode.copySubtree(node.getAST(), node);
//    }
//
//    private boolean alwaysTrue() {
//        return true;
//    }

//    private class NodeUpdater extends ASTVisitorAdaptor
//    {
//        private Map<ASTNode, ASTNode> updatedNodeLookup;
//
//        public NodeUpdater(Map<ASTNode, ASTNode> updatedNodeLookup) {
//            this.updatedNodeLookup = updatedNodeLookup;
//        }
//
//        @Override public boolean beforeVisitEdge(ASTNode node,
//            StructuralPropertyDescriptor edge)
//        {
//            if (node == null)
//                return false;
//            ASTNode replacement = updatedNodeLookup.get(node);
//            if (replacement != null) {
//                System.err.printf("I saw that %s [%x] needed to be %s [%x]%n", node,
//                    identityHashCode(node), replacement, identityHashCode(replacement));
//
//                ASTNode parent = node.getParent();
//                StructuralPropertyDescriptor spd = node.getLocationInParent();
//                ASTNode unparentedReplacement = Entity.copySubtree(node.getAST(),
//                    replacement);
//                if (spd.isChildProperty()) {
//                    parent.setStructuralProperty(spd, unparentedReplacement);
//                }
//                else if (spd.isChildListProperty()) {
//                    ChildListPropertyDescriptor clpd = (ChildListPropertyDescriptor)spd;
//                    List list = (List)parent.getStructuralProperty(clpd);
//                    int i = list.indexOf(node);
//                    if (i == -1) {
//                        ArcumError
//                            .fatalError("Internal NodeUpdater error: shouldn't happen%n");
//                    }
//                    list.set(i, unparentedReplacement);
//                }
//            }
//            return true;
//        }
//
//        @Override public boolean visitASTNode(ASTNode node,
//            StructuralPropertyDescriptor edge)
//        {
//            if (node == null) {
//                return false;
//            }
//            return super.visitASTNode(node, edge);
//        }
//    }

    public static Map<String, Object> values(List<String> names, Object... values)
    {
        Map<String, Object> result = Maps.newHashMap();
        if (names.size() != values.length) {
            ArcumError.fatalError("Internal error detected in values method");
        }
        for (int i=0; i<names.size(); ++i) {
            result.put(names.get(i), values[i]);
        }
        return result;
    }

    public ASTNode getReplacementNode(ASTNode original) {
        if (rootNode != null) {
            return rootNode;
        }
        else {
            for (Object member : values.values()) {
                if (member instanceof ASTNodeReplacer) {
                    ASTNodeReplacer nodeReplacer = (ASTNodeReplacer)member;
                    return nodeReplacer.generateReplacement(original);
                }
            }
            ArcumError.fatalError("Internal error: No ASTNodeReplacer found");
            return null;
        }
    }

    public Map<String, EntityType> getTypes() {
        List<FormalParameter> formals = type.getFormals();
        Map<String, EntityType> result = Maps.newTreeMap();
        for (FormalParameter formal : formals) {
            result.put(formal.getIdentifier(), formal.getType());
        }
        return result;
    }
}