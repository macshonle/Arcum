package edu.ucsd.arcum.builders;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.exceptions.ArcumError;
import edu.ucsd.arcum.exceptions.FatalArcumError;
import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.exceptions.UserCompilationProblem;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.interpreter.query.OptionMatchTable;
import edu.ucsd.arcum.ui.UIUtil;
import edu.ucsd.arcum.util.SystemUtil;

public class ArcumBuilder extends IncrementalProjectBuilder
{
    public ArcumBuilder() {
        if (DEBUG) {
            System.out.printf("Creating a new builder! %d%n",
                    System.identityHashCode(this));
        }
    }

    public static ArcumDeclarationTable reparseArcumCode(IProject project, IProgressMonitor monitor)
        throws CoreException
    {
        if (UIUtil.getNumberOfErrors(project) > 0) {
            throw new FatalArcumError("Project has errors, abort operation");
        }
        System.out.printf("Reparsing Arcum code in %s%n", project.getName());

        ArcumDeclarationTable symbTab = ArcumDeclarationTable.newSymbolTable(project);
        monitor.beginTask("Compiling Arcum Source Files", IProgressMonitor.UNKNOWN);
        ArcumSourceBuilder arcumSourceBuilder = new ArcumSourceBuilder(symbTab, monitor);
        project.accept(arcumSourceBuilder, 0);

        if (arcumSourceBuilder.getNumberOfErrors() == 0) {
            symbTab.typeCheck();
        }
        else {
            ArcumError.stop();
        }

        return symbTab;
    }

    @Override
    protected IProject[] build(final int kind, Map args, IProgressMonitor monitor)
            throws CoreException
    {
        IWorkspaceRunnable doBuild = new IWorkspaceRunnable() {
            public void run(IProgressMonitor monitor) throws CoreException {
                try {
                    if (kind == IncrementalProjectBuilder.FULL_BUILD) {
                        fullBuild(monitor);
                    }
                    else {
                        IResourceDelta delta = getDelta(getProject());
                        if (delta == null) {
                            fullBuild(monitor);
                        }
                        else {
                            incrementalBuild(delta, monitor);
                        }
                    }
                }
                catch (ArcumError e) {
                    e.printStackTrace(System.out);
                    e.printStackTrace(SystemUtil.getOutStream());
                }
                catch (CoreException e) {
                    e.printStackTrace(System.err);
                    e.printStackTrace(SystemUtil.getErrStream());
                    throw e;
                }
                catch (RuntimeException e) {
                    e.printStackTrace(System.err);
                    e.printStackTrace(SystemUtil.getErrStream());
                    throw e;
                }
            }
        };
        ResourcesPlugin.getWorkspace().run(doBuild, null, IWorkspace.AVOID_UPDATE, monitor);
        return null;
    }

    // Note that this fullBuild pass already assumes that the Java builder has
    // done its passes, which may have either been full or incremental.
    private void fullBuild(IProgressMonitor monitor) throws CoreException {
        if (DEBUG) System.out.printf("Starting fullBuild%n");

        IProject project = getProject();
        deleteArcumMarkers(project);

        try {
            ArcumDeclarationTable symbTab = reparseArcumCode(project, monitor);
            List<OptionMatchTable> matchedEntities = symbTab.makeEntityTables();
            // XXX (!!!): I believe this is redundant, but maybe there was a reason for it
//            for (OptionMatchTable entityTable: matchedEntities) {
//                entityTable.checkExtraDefinitionConditions();
//            }
        }
        catch (UserCompilationProblem ucp) {
            SourceLocation position = ucp.getPosition();
            ArcumError.fatalUserError(position, "%s", ucp.getMessage());
        }
        finally {
            monitor.done();
        }
    }
    
    private static void deleteArcumMarkers(IProject project) {
        try {
            project.deleteMarkers(ArcumPlugin.MARKER_ID, false, IResource.DEPTH_INFINITE);
        }
        catch (CoreException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor)
        throws CoreException
    {
        System.out.printf("Starting incrementalBuild%n");
        
        // we still do a "fullBuild" of the .arcum files, but the delta visitor
        // makes sure the needed Java files get new ASTs MACNEIL: in the future
        // when only one AST at a time is stored in memory the two types of
        // builds will be more similar
        delta.accept(new ArcumBuildDeltaVisitor());
        fullBuild(monitor);
    }

    protected void startupOnInitialize() {
        // add builder init logic here
    }

    protected void clean(IProgressMonitor monitor) {
        // add builder clean logic here
        deleteArcumMarkers(getProject());
    }
}
