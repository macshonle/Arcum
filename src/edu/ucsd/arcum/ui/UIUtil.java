package edu.ucsd.arcum.ui;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.*;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

import edu.ucsd.arcum.EclipseUtil;
import edu.ucsd.arcum.util.StringUtil;

public class UIUtil
{
    public static void asyncOpenEditor(final IFile file, final int offset, final int length) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                try {
                    openEditor(file, offset, length);
                }
                catch (PartInitException e) {
                    throw new RuntimeException(String.format("Can't open editor on %s", file));
                }
            }
        });
    }
    
    private static void openEditor(IFile file, int offset, int length) throws PartInitException {
        IWorkbenchWindow workbenchWindow;
        IWorkbenchPage page;
        IEditorPart editor;

        workbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        page = workbenchWindow.getActivePage();
        editor = IDE.openEditor(page, file, true);
        if (editor instanceof ITextEditor) {
            ITextEditor textEditor = (ITextEditor)editor;
            textEditor.setHighlightRange(offset, length, true);
            textEditor.selectAndReveal(offset, length);
        }
    }
    
    public static void notify(final String info, final String title) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                final Shell shell = EclipseUtil.getShell();
                MessageDialog.openInformation(shell, title, info);
            }
        });
    }
    
    public static void error(final String info, final String title) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                final Shell shell = EclipseUtil.getShell();
                MessageDialog.openError(shell, title, info);
            }
        });
    }

    public static boolean existsUnsavedEditor() {
        IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
        for (IWorkbenchWindow window: windows) {
            for (IWorkbenchPage page: window.getPages()) {
                IEditorPart[] dirtyEditors = page.getDirtyEditors();
                if (dirtyEditors != null && dirtyEditors.length > 0)
                    return true;
            }
        }
        return false;
    }

    // checks to see if the project has any errors: if so, it brings up a dialog
    // and returns true. Returns false otherwise.
    public static boolean hasErrors(IProject project) {
        int errors = UIUtil.getNumberOfErrors(project);
        if (errors > 0) {
            String numErrors;
            if (errors > 1)
                numErrors = String.format("There are %d errors", errors);
            else
                numErrors = "There is an error";
            String message = String.format(
                    "%s in \"%s\". Resolve all Java compilation errors before continuing.",
                    numErrors, project.getName());
            notify(message, "There Are Errors");
            return true;
        }
        return false;
    }

    public static int getNumberOfErrors(IProject project) {
        int errors = 0;
        try {
            IMarker[] markers;
            markers = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
            for (IMarker marker: markers) {
                if (marker.getAttribute("severity", 1) > 1) {
                    ++errors;
                }
                if (false&&DEBUG) {
                    System.out.printf("%s%n", StringUtil.separate(marker.getAttributes()));
                }
            }
        }
        catch (CoreException e1) {
            errors = 1;
        }
        return errors;
    }
}
