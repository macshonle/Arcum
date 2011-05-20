package edu.ucsd.arcum.ui.wizards;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

public class ArcumRefactoringWizard extends RefactoringWizard
{
    public ArcumRefactoringWizard(Refactoring refactoring) {
        super(refactoring, RefactoringWizard.CHECK_INITIAL_CONDITIONS_ON_OPEN);
    }

    @Override
    protected void addUserInputPages() {
        // MACNEIL: none to add, so far
    }
}