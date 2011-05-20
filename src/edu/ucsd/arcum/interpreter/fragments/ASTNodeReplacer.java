package edu.ucsd.arcum.interpreter.fragments;

import org.eclipse.jdt.core.dom.ASTNode;

public interface ASTNodeReplacer
{
    ASTNode generateReplacement(ASTNode original);
}
