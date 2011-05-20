package edu.ucsd.arcum.builders;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.EclipseUtil;
import edu.ucsd.arcum.util.ErrorCode;

public class ParseArcumCodeOperation implements IRunnableWithProgress
{
    private final IProject project;
    private boolean fullBuild;
    private int numErrs;

    private ParseArcumCodeOperation(IProject project, boolean fullBuild) {
        this.project = project;
        this.fullBuild = fullBuild;
    }

    public void run(IProgressMonitor monitor)
        throws InvocationTargetException, InterruptedException
    {
        try {
            if (fullBuild && project.hasNature(ArcumPlugin.NATURE_ID)) {
                // MACNEIL: if this starts a new Thread then we need to wait
                // for it
                project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
            }
            else {
                ArcumBuilder.reparseArcumCode(project, monitor);
            }
        }
        catch (CoreException e) {
            e.printStackTrace();
        }
    }

    // update only the .arcum source files
    public static @ErrorCode boolean parseArcumCodeWithDialog(IProject project) {
        try {
            ProgressMonitorDialog dialog;
            ParseArcumCodeOperation runnable;

            runnable = new ParseArcumCodeOperation(project, false);
            dialog = new ProgressMonitorDialog(EclipseUtil.getShell());
            dialog.run(true, false, runnable);
            return runnable.numErrs == 0;
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // update the .arcum source files and rematch all entities
    public static @ErrorCode boolean rematchAllCodeWithDialog(IProject project) {
        try {
            ProgressMonitorDialog dialog;
            ParseArcumCodeOperation runnable;

            runnable = new ParseArcumCodeOperation(project, true);
            dialog = new ProgressMonitorDialog(EclipseUtil.getShell());
            dialog.run(true, false, runnable);
            return runnable.numErrs == 0;
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}