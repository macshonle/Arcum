package edu.ucsd.arcum.interpreter.query;

import org.eclipse.jdt.core.dom.*;

// An ASTMatcher that doesn't care about the (full) qualification of type names.
public class ModuloQualificationASTMatcher extends ASTMatcher
{
    @Override
    public boolean match(QualifiedType node, Object other) {
        boolean result = super.match(node, other);
        if (result == false) {
            result = tryCompareTypes(node, other);
        }
        return result;
    }
    
    @Override
    public boolean match(SimpleType node, Object other) {
        boolean result = super.match(node, other);
        if (result == false) {
            result = tryCompareTypes(node, other);
        }
        return result;
    }
    
    @Override public boolean match(TypeLiteral node, Object other) {
        boolean result = super.match(node, other);
        if (result == false) {
            Type type = node.getType();
            if (other instanceof TypeLiteral) {
                TypeLiteral otherTypeLiteral = (TypeLiteral)other;
                Type otherType = otherTypeLiteral.getType();
                result = tryCompareTypes(type, otherType);
            }
        }
        return result;
    }

    private boolean tryCompareTypes(Type t1, Object other) {
        boolean result = false;
        if (other instanceof Type) {
            Type t2 = (Type)other;
            ITypeBinding tb1 = (ITypeBinding)EntityDataBase.resolveBindingNullOK(t1);
            ITypeBinding tb2 = (ITypeBinding)EntityDataBase.resolveBindingNullOK(t2);
            if (tb1 != null && tb2 != null) {
                result = tb1.isEqualTo(tb2);
            }
            else if (tb1 != null && t2 instanceof SimpleType) {
                result = compareTypeBindingToType(tb1, (SimpleType)t2);
            }
            else if (tb2 != null && t1 instanceof SimpleType) {
                result = compareTypeBindingToType(tb2, (SimpleType)t1);
            }
        }
        return result;
    }

    private boolean compareTypeBindingToType(ITypeBinding tb, SimpleType simpleType) {
        boolean result = false;
        Name name = simpleType.getName();
        if (name instanceof QualifiedName) {
            String qn1 = ((QualifiedName)name).getFullyQualifiedName();
            String qn2 = tb.getQualifiedName();
            result = qn1.equals(qn2);
        }
        return result;
    }
}
