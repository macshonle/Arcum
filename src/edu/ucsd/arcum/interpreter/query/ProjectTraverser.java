package edu.ucsd.arcum.interpreter.query;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;

import edu.ucsd.arcum.EclipseUtil;
import edu.ucsd.arcum.util.StringUtil;

// ProjectTraverser
public class ProjectTraverser
{
    public interface ICompilationUnitVisitor
    {
        void visitCompilationUnit(CompilationUnit compilationUnit);
    }

    private static Map<String, CompilationUnit> cachedParsedASTs;
    static {
        cachedParsedASTs = new HashMap<String, CompilationUnit>();
    }

    private final IProject project;
    private final IJavaProject javaProject;
    private final String message;
    private IProgressMonitor monitor;
    private int totalFilesSeen;

    // initialized when runTraversal is called
    private IResourceProxyVisitor resourceVisitor;
    private IRunnableWithProgress runnable;

    public ProjectTraverser(IProject project, String message) {
        this.project = project;
        this.javaProject = JavaCore.create(project);
        this.message = message;
        this.monitor = null;
        this.totalFilesSeen = 0;
    }

    public void runTraversal(ICompilationUnitVisitor visitor) {
        this.resourceVisitor = new ProjectTraverser.ResourceVisitor(visitor);
        this.runnable = new ProjectTraverser.RunnableWithProgress();

        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                ProgressMonitorDialog dialog;
                dialog = new ProgressMonitorDialog(EclipseUtil.getShell());
                try {
                    ProjectTraverser.this.totalFilesSeen = 0;
                    dialog.run(true, false, ProjectTraverser.this.runnable);
                    if (DEBUG)
                        System.out.printf("Saw %d files%n", totalFilesSeen);
                }
                catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void markSourceFileDirty(String filePath) {
        if (DEBUG) {
            if (cachedParsedASTs.containsKey(filePath)) {
                System.out.printf("Deleting cached parse for %s%n", filePath);
            }
        }
        cachedParsedASTs.remove(filePath);
    }

    private class ResourceVisitor implements IResourceProxyVisitor
    {
        private ICompilationUnitVisitor visitor;

        public ResourceVisitor(ICompilationUnitVisitor visitor) {
            this.visitor = visitor;
        }

        @Override public boolean visit(IResourceProxy proxy) throws CoreException {
            if (proxy.getType() == IResource.FILE) {
                IFile file = (IFile)proxy.requestResource();
                if (isJavaSourceFile(file)) {
                    ++totalFilesSeen;
                    if (monitor != null) {
                        monitor.subTask(file.getName());
                    }

                    CompilationUnit compilationUnit = parseJavaSource(file);
                    visitor.visitCompilationUnit(compilationUnit);

                    if (monitor != null) {
                        monitor.worked(1);
                    }
                }
            }
            return true;
        }
    }

    private class RunnableWithProgress implements IRunnableWithProgress
    {

        @Override public void run(IProgressMonitor monitor)
            throws InvocationTargetException, InterruptedException
        {
            try {
                project.accept(new IResourceProxyVisitor() {
                    public boolean visit(IResourceProxy resource) throws CoreException {
                        if (resource.getType() == IResource.FILE) {
                            IFile file = (IFile)resource.requestResource();
                            if (isJavaSourceFile(file)) {
                                ++totalFilesSeen;
                            }
                        }
                        return true;
                    }
                }, 0);

                // URGENT: for real, need to see if project has errors

                String userMessage;
                userMessage = String.format("Arcum: Analyzing \"%s\"", project.getName());
                if (message != null && !message.equals("")) {
                    userMessage = String.format("%s - %s", userMessage, message);
                }
                monitor.beginTask(userMessage, totalFilesSeen);
                ProjectTraverser.this.monitor = monitor;
                project.accept(ProjectTraverser.this.resourceVisitor, 0);
                monitor.done();
            }
            catch (CoreException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private boolean isJavaSourceFile(IFile file) throws CoreException {
        IContentDescription contentDescription = file.getContentDescription();
        if (contentDescription != null) {
            IContentType type = contentDescription.getContentType();
            String id = type.getId();
            if (id.equals(JavaCore.JAVA_SOURCE_CONTENT_TYPE)) {
                boolean onClasspath = javaProject.isOnClasspath(file);
                return onClasspath;
            }
        }
        return false;
    }

    private CompilationUnit parseJavaSource(IFile file) {
        String filePath = file.toString();
        CompilationUnit result = cachedParsedASTs.get(filePath);
        if (result == null) {
            IJavaProject project = JavaCore.create(file.getProject());
            ASTParser parser = ASTParser.newParser(AST.JLS3);

            ICompilationUnit source = JavaCore.createCompilationUnitFrom(file);
            parser.setProject(project); // NOTE: this line might not be needed
            parser.setSource(source); // sets compiler options too
            parser.setResolveBindings(true);

            result = (CompilationUnit)parser.createAST(null);
            cachedParsedASTs.put(filePath, result);

            if (DEBUG) {
                AST ast = result.getAST();
                System.out.printf("Parsed %s, result=%d [%s]%n", file.toString(), System
                    .identityHashCode(result), StringUtil.firstLine(result.toString()));
            }
        }
        return result;
    }
}