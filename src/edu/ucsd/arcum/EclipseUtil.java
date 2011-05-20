package edu.ucsd.arcum;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;

// Facade for commonly-done operations in Eclipse
public class EclipseUtil
{
    public static Shell getShell() {
        IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (activeWorkbenchWindow != null) {
        	return activeWorkbenchWindow.getShell();
        }
        return null;
    }

    public static List<IProject> getOpenProjects() {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = root.getProjects();
        ArrayList<IProject> openProjects = new ArrayList<IProject>();
        for (IProject element: projects) {
            if (element.isOpen()) {
                openProjects.add(element);
            }
        }
        return openProjects;
    }

    public static void refreshPackageExplorer() {
        UIJob job = new UIJob("Refresh Package Explorer") {
            @Override
            public IStatus runInUIThread(IProgressMonitor monitor) {
                PackageExplorerPart explorer;
                explorer = PackageExplorerPart.getFromActivePerspective();
                if (explorer != null) {
                    explorer.getTreeViewer().refresh();
                }
                return Status.OK_STATUS;
            }
        };
        job.schedule();
    }
}
