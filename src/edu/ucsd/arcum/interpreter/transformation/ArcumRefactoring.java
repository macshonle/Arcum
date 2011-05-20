package edu.ucsd.arcum.interpreter.transformation;

import static org.eclipse.ltk.core.refactoring.RefactoringStatus.createFatalErrorStatus;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.*;

public class ArcumRefactoring extends Refactoring
{
    private String message;
    private CodeRewriter rewriter;

    public ArcumRefactoring(String message, CodeRewriter cuRewriters) {
        this.message = message;
        this.rewriter = cuRewriters;
    }

    // First check for preconditions
    @Override
    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
        throws CoreException, OperationCanceledException
    {
        RefactoringStatus status = new RefactoringStatus();
        try {
            pm.beginTask("Checking preconditions...", IProgressMonitor.UNKNOWN);
            if (false) {
                String message = String.format(
                        "Compilation unit '%s' contains compile errors.",
                        ""/*.getCompilationUnit().getElementName()*/);
                status.merge(createFatalErrorStatus(message));
            }
        }
        finally {
            pm.done();
        }
        return status;
    }

    // Final check for preconditions
    @Override
    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
        throws CoreException, OperationCanceledException
    {
        RefactoringStatus status = new RefactoringStatus();
        return status;
    }

    @Override
    public Change createChange(IProgressMonitor pm)
        throws CoreException, OperationCanceledException
    {
        try {
            pm.beginTask("Creating change...", IProgressMonitor.UNKNOWN);
            Change[] changes = rewriter.getChanges();
            CompositeChange result = new CompositeChange(message, changes );
            return result;
        }
        finally {
            pm.done();
        }
    }

    @Override
    public String getName() {
        return message;
    }
}