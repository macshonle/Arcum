package edu.ucsd.arcum.interpreter.parser;

import static edu.ucsd.arcum.util.StringUtil.separate;

import java.util.List;

import org.eclipse.jdt.core.dom.*;

import edu.ucsd.arcum.interpreter.query.ASTTraverseTable;
import edu.ucsd.arcum.interpreter.query.IASTVisitor;
import edu.ucsd.arcum.util.Indenter;

public class ASTPrinter implements IASTVisitor
{
    private ASTTraverseTable traverseTable;

    // initialized after print is called
    private Indenter indenter;

    public ASTPrinter() {
        this.traverseTable = new ASTTraverseTable();
    }
    
    public void print(CompilationUnit javaSourceUnit) {
        this.indenter = new Indenter("  ");
        traverseTable.traverseAST(javaSourceUnit, this);
    }

    public boolean visitASTNode(ASTNode node, StructuralPropertyDescriptor edge) {
        System.out.printf("%s", indenter.newLine());
        if (isAtomic(node)) {
            System.out.printf("%s %s", node.toString().trim(), className(node));
            return false;
        }
        System.out.printf(className(node));
        if (node == null) {
            return false;
        }
        else {
            indenter.indent();
            return true;
        }
    }

    public void afterVisitASTNodesChildren(ASTNode node) {
        indenter.unindent();
    }
    
    public boolean beforeVisitEdge(ASTNode parent, StructuralPropertyDescriptor edge) {
        System.out.printf("%s", indenter.newLine());
        System.out.printf("<%s>", edge.getId());
        indenter.indent();
        return true;
    }

    public void afterVisitEdge(ASTNode parent, StructuralPropertyDescriptor edge) {
        indenter.unindent();
    }

    public boolean preVisitASTNodeList(ASTNode parent, List nodes, StructuralPropertyDescriptor edge) {
        if (nodes.size() == 0) {
            System.out.printf("%s[an empty list]", indenter.newLine());
            return false;
        }
        else if (allModifiers(nodes, edge)) {
            System.out.printf("%s[%s]", indenter.newLine(), separate(nodes));
            return false;
        }
        return true;
    }

    public void visitSimpleProperty(Object property, StructuralPropertyDescriptor edge) {
        System.out.printf("%s[%s] %s", indenter.newLine(),
                String.valueOf(property), className(property));
    }

    // a null protected class name extractor
    private String className(Object obj) {
        if (obj == null)
            return "null";
        else
            return obj.getClass().getSimpleName();
    }
    
    private boolean isAtomic(ASTNode node) {
        return node instanceof Name
            || node instanceof StringLiteral
            || node instanceof PackageDeclaration
            || node instanceof Modifier
            || node instanceof Type;
    }
    
    private boolean allModifiers(List nodes, StructuralPropertyDescriptor edge) {
        if (!edge.getId().equals("modifiers")) {
            return false;
        }
        for (Object node: nodes) {
            if (!(node instanceof Modifier))
                return false;
        }
        return true;
    }

    public void postVisitASTNodeListElement(StructuralPropertyDescriptor spd, List nodes) {
        // intentionally left blank
    }
}