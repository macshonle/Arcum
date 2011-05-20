package edu.ucsd.arcum.interpreter.parser;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import edu.ucsd.arcum.interpreter.query.IASTVisitor;

public class ASTVisitorAdaptor implements IASTVisitor
{
    public void afterVisitASTNodesChildren(ASTNode node) {
        // intentionally left blank
        ;
    }

    public void afterVisitEdge(ASTNode parent, StructuralPropertyDescriptor edge) {
        // intentionally left blank
        ;
    }

    public boolean beforeVisitEdge(ASTNode parent, StructuralPropertyDescriptor edge) {
        // intentionally just a return
        return true;
    }

    public void postVisitASTNodeListElement(StructuralPropertyDescriptor spd, List nodes)
    {
        // intentionally left blank
        ;
    }

    public boolean preVisitASTNodeList(ASTNode parent, List nodes,
        StructuralPropertyDescriptor edge)
    {
        // intentionally just a return
        return true;
    }

    public boolean visitASTNode(ASTNode node, StructuralPropertyDescriptor edge) {
        // intentionally just a return
        return true;
    }

    public void visitSimpleProperty(Object property, StructuralPropertyDescriptor edge) {
        // intentionally left blank
        ;
    }
}