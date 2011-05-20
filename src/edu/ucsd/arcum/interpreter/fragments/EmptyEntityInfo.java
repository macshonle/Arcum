package edu.ucsd.arcum.interpreter.fragments;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

// An entity can be empty when it is implicit in the program. For example, in the
// expression "f()" the target expression is empty, but is implicitly "this", as if
// it were "this.f()". There are times when we want to match against these empty
// references too.
//
// MACNEIL: what to do about someone trying to match with the pattern "this"? Is
// that a special case, or should they only get the explicit references? This is
// kind of a messy issue. One solution is to disallow matching like that, yet that
// might feel a little inconsistent because matching for "super" calls would be
// desirable.
public class EmptyEntityInfo
{
    private ASTNode parent;
    private StructuralPropertyDescriptor edge;

    public EmptyEntityInfo(ASTNode parent, StructuralPropertyDescriptor edge) {
        this.parent = parent;
        this.edge = edge;
    }

    public ASTNode getParent() {
        return parent;
    }

    public StructuralPropertyDescriptor getEdge() {
        return edge;
    }
}