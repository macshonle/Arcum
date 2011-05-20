package edu.ucsd.arcum.interpreter.query;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

public interface IASTVisitor
{
    // Called to visit the given ASTNode, which was found via the given edge
    // (only at the top level is the edge null). This ASTNode may be on a list
    // of related edges: the total number of edges is given by totalElements
    // and this particular item is the "num"th item.
    // 
    // Return true if you want to visit the children (if any) of the node.
    boolean visitASTNode(ASTNode node, StructuralPropertyDescriptor edge);
    
    // Called after the given ASTNode's children have been visited, but only if
    // the previous visitASTNode call returned true.
    void afterVisitASTNodesChildren(ASTNode node);

    // Return false if the edge should not be visited. If so, afterVisitEdge
    // will not get called.
    boolean beforeVisitEdge(ASTNode parent, StructuralPropertyDescriptor edge);

    //
    void afterVisitEdge(ASTNode parent, StructuralPropertyDescriptor edge);

    // Called before an entire list is visited. Return true if the elements
    // should be visited normally; false if they should only be visited by
    // this method.
    boolean preVisitASTNodeList(ASTNode parent, List nodes, StructuralPropertyDescriptor edge);

    // Called when preVisitASTNodeList return true and an element on that list
    // was just visited.
    void postVisitASTNodeListElement(StructuralPropertyDescriptor spd, List nodes);

    // Visit a terminal node, e.g. a String, which is not an ASTNode. These
    // simple properties have no children, so there is no return type.
    void visitSimpleProperty(Object property, StructuralPropertyDescriptor edge);
}