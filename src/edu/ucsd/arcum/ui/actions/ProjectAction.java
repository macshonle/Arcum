package edu.ucsd.arcum.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

// Apply an action to the a set of selected projects. Code copied and adapted
// the AJDT source files RemoveAJNatureAction.java and AJDTUtils.java.
public abstract class ProjectAction implements IObjectActionDelegate
{
    private ArrayList<IProject> selectedProjects = new ArrayList<IProject>();
    private boolean actionOnlyForOpenProjects;

    protected ProjectAction(boolean actionOnlyForOpenProjects) {
        this.actionOnlyForOpenProjects = actionOnlyForOpenProjects;
    }

    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    }

    public void run(IAction action) {
        for (final IProject project : selectedProjects) {
            if (actionOnlyForOpenProjects && !project.isOpen()) {
                continue;
            }
            try {
                // wrap up the operation so that an autobuild is not triggered
                // in the middle of the conversion
                new WorkspaceModifyOperation() {
                    @Override
                    protected void execute(IProgressMonitor monitor)
                            throws CoreException {
                        doAction(project);
                    }
                }.run(null);
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    abstract protected void doAction(IProject project) throws CoreException;

    public void selectionChanged(IAction action, ISelection sel) {
        selectedProjects.clear();
        boolean enable = true;
        if (sel instanceof IStructuredSelection) {
            IStructuredSelection selection = (IStructuredSelection)sel;
            for (Iterator iter = selection.iterator(); iter.hasNext();) {
                Object object = iter.next();
                if (object instanceof IAdaptable) {
                    IProject project = (IProject)((IAdaptable)object)
                            .getAdapter(IProject.class);
                    if (project != null) {
                        selectedProjects.add(project);
                    }
                    else {
                        enable = false;
                        break;
                    }
                }
                else {
                    enable = false;
                    break;
                }
            }
            action.setEnabled(enable);
        }
    }
}
