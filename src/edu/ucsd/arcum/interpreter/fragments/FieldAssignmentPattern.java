package edu.ucsd.arcum.interpreter.fragments;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;

import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.interpreter.query.IEntityLookup;
import edu.ucsd.arcum.interpreter.satisfier.BindingMap;

public class FieldAssignmentPattern extends ProgramFragment
{
    private FieldAccessPattern fieldAccessPattern;
    private ProgramFragment rightHandSidePattern;

    public FieldAssignmentPattern(FieldAccessPattern fieldAccessPattern,
        ProgramFragment rightHandSidePattern)
    {
        this.fieldAccessPattern = fieldAccessPattern;
        this.rightHandSidePattern = rightHandSidePattern;

        fieldAccessPattern.setLeftHandSide(true);
    }

    @Override protected void buildString(StringBuilder buff) {
        buff.append(getIndenter());
        buff.append(String.format("(FieldAssignmentPattern%n"));
        getIndenter().indent();
        {
            buff.append(getIndenter());
            buff.append(String.format("<fieldAccessPattern>%n"));
            fieldAccessPattern.buildString(buff);
            buff.append(String.format("%n"));
            buff.append(getIndenter());
            buff.append(String.format("<rightHandSide>%n"));
            rightHandSidePattern.buildString(buff);
        }
        buff.append(")");
        getIndenter().unindent();
    }

    @Override public BindingMap generateNode(IEntityLookup lookup, AST ast) {
        BindingMap lhs = fieldAccessPattern.generateNode(lookup, ast);
        BindingMap rhs = rightHandSidePattern.generateNode(lookup, ast);
        
        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide((Expression)lhs.getResult());//copy(ast, Expression.class, lhs.getResult()));
        assignment.setRightHandSide(copy(ast, Expression.class, (ASTNode)rhs.getResult()));
        BindingMap theta = bindRoot(assignment).consistentMerge(lhs, rhs);
        return theta;
    }

    @Override protected BindingMap matchesASTNode(ASTNode node) {
        if (node == null) {
            ArcumError.fatalError("Gulp, this is bad");
            return null;
        }
        if (node instanceof Assignment) {
            Assignment assignment = (Assignment)node;

            Expression leftHandSide = assignment.getLeftHandSide();
            Expression rightHandSide = assignment.getRightHandSide();

            BindingMap lhsMatches = fieldAccessPattern.matches(leftHandSide);
            if (lhsMatches == null) {
                return null;
            }

            BindingMap rhsMatches = rightHandSidePattern.matches(rightHandSide);
            if (rhsMatches == null) {
                return null;
            }

            BindingMap result = bindRoot(node);
            result.addBindings(lhsMatches);
            result.addBindings(rhsMatches);
            return result;
        }

        return null;
    }
}