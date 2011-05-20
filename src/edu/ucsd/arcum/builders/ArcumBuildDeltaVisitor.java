package edu.ucsd.arcum.builders;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;

import edu.ucsd.arcum.interpreter.query.ProjectTraverser;

public class ArcumBuildDeltaVisitor implements IResourceDeltaVisitor
{
    public boolean visit(IResourceDelta delta) throws CoreException {
        IResource resource = delta.getResource();
        if (resource.getType() == IResource.FILE) {
            if (resource.isDerived()) {
                // skip over derived sources; they are either not .arcum source
                // files, or are copies of .arcum source files already seen in
                // the src directory
                return false;
            }
            String filePath = resource.toString();
            System.out.printf("Delta visitor visited and marked dirty %s%n", filePath);
            ProjectTraverser.markSourceFileDirty(filePath);
        }
        return true;
    }
}
