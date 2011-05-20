package edu.ucsd.arcum.builders;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.interpreter.parser.ArcumSourceFileParser;
import edu.ucsd.arcum.interpreter.query.ArcumDeclarationTable;
import edu.ucsd.arcum.util.FileUtil;

// Builds only the .arcum files visited
public class ArcumSourceBuilder implements IResourceProxyVisitor
{
    private static int counter = 0;
    
    private ArcumDeclarationTable table;
    private IProgressMonitor monitor;
    private int numErrs;
    
    public ArcumSourceBuilder(ArcumDeclarationTable table, IProgressMonitor monitor) {
        this.table = table;
        this.monitor = monitor;
        this.numErrs = 0;
    }

    public boolean visit(IResourceProxy resource) throws CoreException {
        if (resource.getType() == IResource.FILE) {
            if (resource.isDerived()) {
                // skip over derived sources; they are either not .arcum source
                // files, or are copies of .arcum source files already seen in
                // the src directory
                return false;
            }
            
            IFile file = (IFile)resource.requestResource();
            IContentDescription contentDescription = file.getContentDescription();
            if (contentDescription == null) {
            	if (DEBUG) {
            		System.out.printf("File %s has no content description%n", file.getName());
            	}
                return false;
            }
            IContentType type = contentDescription.getContentType();
            String id = type.getId();
            //if (DEBUG) System.out.printf("#%d, Looking at %s (%s)%n", counter, file, id);
            ++counter;
            if (id.equals(ArcumPlugin.SOURCE_ID)) {
                String msg = String.format("Compiling %s", file);
                monitor.subTask(msg);

                if (DEBUG) System.out.printf("%s (%s)%n", msg, id);

                String source;
                String name;
                ArcumSourceFileParser parser;
                
                source = FileUtil.readFile(file);
                name = file.getFullPath().lastSegment();
                parser = new ArcumSourceFileParser(source, file, name, file.getProject());
                numErrs += parser.parseArcumSource(table);
            }
        }
        return true;
    }

    public int getNumberOfErrors() {
        return numErrs;
    }
}