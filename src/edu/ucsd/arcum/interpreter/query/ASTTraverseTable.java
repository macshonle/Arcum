package edu.ucsd.arcum.interpreter.query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import com.google.common.collect.Maps;

import edu.ucsd.arcum.util.ClassImplements;

// A traverse table can inspect AST nodes and invoke the property descriptor
// accessor methods available from the node (in order to allow visits to all
// of the node's children).
//
// To avoid recomputing the same relations all instances share the same lookup
// table.
public class ASTTraverseTable
{
    private static final Map<Class<? extends ASTNode>, StructuralPropertyDescriptor[]> lookup;

    static {
        // POSSIBLE_ECLIPSE_BUG: try: ASTTraversTable.lookup = ...
        lookup = Maps.newConcurrentHashMap();
    }

    public void traverseAST(ASTNode node, IASTVisitor visitor) {
        traverseNode(node, node.getLocationInParent(), visitor);
    }

    private void traverseNode(ASTNode node, StructuralPropertyDescriptor edge,
        IASTVisitor visitor)
    {
        boolean visitChildren = visitor.visitASTNode(node, edge);

        if (visitChildren) {
            StructuralPropertyDescriptor[] spds = getProperties(node);
            for (StructuralPropertyDescriptor spd : spds) {
                boolean doVisit = visitor.beforeVisitEdge(node, spd);
                if (!doVisit) {
                    continue;
                }

                Object property = node.getStructuralProperty(spd);

                if (spd.isSimpleProperty()) {
                    visitor.visitSimpleProperty(property, spd);
                }
                else if (spd.isChildProperty()) {
                    ASTNode child = (ASTNode)property;
                    traverseNode(child, spd, visitor);
                }
                else if (spd.isChildListProperty()) {
                    List children = (List)property;
                    boolean iterate = visitor.preVisitASTNodeList(node, children, spd);
                    if (iterate) {
                        for (Object obj : children) {
                            traverseNode((ASTNode)obj, spd, visitor);
                            visitor.postVisitASTNodeListElement(spd, children);
                        }
                    }
                }
                else
                    assert false;

                visitor.afterVisitEdge(node, spd);
            }
            visitor.afterVisitASTNodesChildren(node);
        }
    }

    public static StructuralPropertyDescriptor[] getProperties(
        @ClassImplements(PropertyDescriptorAccessor.class) ASTNode node)
    {
//        List properties = (List)ClassMethod.invoke(PropertyDescriptorAccessor.class, node.getClass(), AST.JLS3);
        Class<? extends ASTNode> nodeClass = node.getClass();
        StructuralPropertyDescriptor[] spds = lookup.get(nodeClass);
        if (spds == null) {
            try {
                Method method;
                List properties;

                method = nodeClass.getMethod("propertyDescriptors", int.class);
                properties = (List)method.invoke(nodeClass, AST.JLS3);
                spds = toArray(properties);
                lookup.put(nodeClass, spds);
            }
            catch (RuntimeException e) {
                throw e;
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return spds;
    }

    private static StructuralPropertyDescriptor[] toArray(List list) {
        StructuralPropertyDescriptor[] result;
        result = new StructuralPropertyDescriptor[list.size()];
        int i = 0;
        for (Object o : list) {
            result[i++] = (StructuralPropertyDescriptor)o;
        }
        return result;
    }

    private static List<ASTNode> subNodes(ASTNode node) {
        StructuralPropertyDescriptor[] properties = getProperties(node);
        List<ASTNode> result = new ArrayList<ASTNode>();
        for (StructuralPropertyDescriptor spd : properties) {
            Object property = node.getStructuralProperty(spd);
            if (spd.isChildProperty()) {
                result.add((ASTNode)property);
            }
            else if (spd.isChildListProperty()) {
                List objects = (List)property;
                for (Object object : objects) {
                    result.add((ASTNode)object);
                }
            }
        }
        return result;
    }
}
