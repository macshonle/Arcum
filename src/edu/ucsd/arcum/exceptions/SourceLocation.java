package edu.ucsd.arcum.exceptions;

import java.util.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.ui.texteditor.MarkerUtilities;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.interpreter.ast.ASTUtil;
import edu.ucsd.arcum.interpreter.ast.expressions.ConstraintExpression;
import edu.ucsd.arcum.interpreter.query.EntityDataBase;
import edu.ucsd.arcum.ui.UIUtil;
import edu.ucsd.arcum.util.AquireReleasePair;
import edu.ucsd.arcum.util.DynamicScope;

public class SourceLocation
{
    public static final SourceLocation GENERATED = new SourceLocation();
    private static DynamicScope<SourceLocation> currentLocation = DynamicScope.newInstance();

    private IResource resource;
    private int charStart;
    private int charEnd;
    private int line;
    private boolean hasLineInformation;

    public static SourceLocation fromEntity(Object entity, EntityDataBase edb) {
        if (entity instanceof ASTNode) {
            return new SourceLocation((ASTNode)entity);
        }
        else if (entity instanceof ITypeBinding) {
            AbstractTypeDeclaration decl = edb.lookupTypeDeclaration((ITypeBinding)entity);
            return new SourceLocation(decl);
        }
        else {
            return new SourceLocation();
        }
    }

    public SourceLocation(ASTNode node) {
        CompilationUnit unit = ASTUtil.findCompilationUnit(node);
        if (unit == null) {
            this.hasLineInformation = false;
        }
        else {
            if (node instanceof AbstractTypeDeclaration) {
                // Highlight the class name as the source of the error instead of the
                // entire class
                node = ((AbstractTypeDeclaration)node).getName();
            }
            this.resource = unit.getJavaElement().getResource();
            this.charStart = node.getStartPosition();
            this.charEnd = charStart + node.getLength();
            this.line = unit.getLineNumber(charStart);
            this.hasLineInformation = true;
        }
    }

    public SourceLocation(IResource resource, int charStart, int charEnd, int line) {
        this.resource = resource;
        this.charStart = charStart;
        this.charEnd = charEnd;
        this.line = line;
        this.hasLineInformation = true;
    }

    public SourceLocation() {
        this.hasLineInformation = false;
    }

    @Override
    public String toString() {
        if (hasLineInformation) {
            return String.format("%s:%d:%d-%d", resource.getFullPath(), line, charStart,
                charEnd);
        }
        return "<no line information available, internal error>";
    }

    public void createMarker(String message) throws CoreException {
        if (resource == null) {
            ArcumError.fatalError("Built-in .arcum source file error?");
        }
        Map<Object, Object> attributes = new HashMap<Object, Object>(7);
//        message = String.format("%s cs=%d ce=%d line=%d", message, charStart, charEnd, line);
        attributes.put(IMarker.MESSAGE, message);
        attributes.put(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
        attributes.put(IMarker.USER_EDITABLE, false);
        if (hasLineInformation) {
            attributes.put(IMarker.CHAR_START, charStart);
            attributes.put(IMarker.CHAR_END, charEnd);
            attributes.put(IMarker.LINE_NUMBER, line);
            attributes.put(IMarker.LOCATION, String.format("line %d", line));
        }
        MarkerUtilities.createMarker(resource, attributes, ArcumPlugin.MARKER_ID);
    }

    public SourceLocation extendedTo(SourceLocation extendedPosition) {
        if (this == GENERATED) {
            return GENERATED;
        }
        else {
            SourceLocation result = new SourceLocation(resource, charStart,
                extendedPosition.charEnd, line);
            result.hasLineInformation = hasLineInformation;
            return result;
        }
    }

    public void openInEditor() {
        if (hasLineInformation) {
            if (resource instanceof IFile) {
                IFile file = (IFile)resource;
                UIUtil.asyncOpenEditor(file, charStart, charEnd - charStart);
            }
        }
    }

    public String getFileName() {
        if (hasLineInformation) {
            return resource.getFullPath().lastSegment();
        }
        else {
            return "-";
        }
    }

    public String getPathName() {
        if (hasLineInformation) {
            return resource.getFullPath().toString();
        }
        else {
            return "-";
        }
    }

    public String getLineAsString() {
        if (line != 0) {
            return String.valueOf(line);
        }
        else {
            return "-";
        }
    }

    // EXAMPLE: this should be a collection of an interface type instead, something
    // where the location can be grabbed from it
    public static SourceLocation coverAll(Collection<ConstraintExpression> conditions) {
        List<SourceLocation> positions = new ArrayList<SourceLocation>();
        for (ConstraintExpression condition : conditions) {
            positions.add(condition.getPosition());
        }
        SourceLocation first = positions.get(0);
        if (positions.size() == 1) {
            return first;
        }
        int lowestCharStart = first.charStart;
        int highestCharEnd = first.charEnd;
        int lowestLine = first.line;
        for (SourceLocation position : positions) {
            lowestCharStart = Math.min(lowestCharStart, position.charStart);
            highestCharEnd = Math.max(highestCharEnd, position.charEnd);
            lowestLine = Math.min(lowestLine, position.line);
            if (!position.resource.equals(first.resource)) {
                // EXAMPLE: find all calls to resource.getFullPath().toString() and
                // replace with static method call Util.toPathString(resource)
                ArcumError.fatalUserError(position, "Arcum: Strange internal error:"
                    + " %s is not the same file as %s", position.resource.getFullPath()
                    .toString(), first.resource.getFullPath().toString());
            }
        }
        SourceLocation result = new SourceLocation(first.resource, lowestCharStart,
            highestCharEnd, lowestLine);
        result.hasLineInformation = first.hasLineInformation;
        return result;
    }

    @AquireReleasePair(aquire="pushLocation", release="popLocation")
    public static void pushLocation(SourceLocation location) {
        currentLocation.push(location);
    }

    public static void popLocation() {
        currentLocation.pop();
    }
    
    // Attempts to identify a source location associated with the current context,
    // set up by pushLocation/popLocation. If no location can be found, the
    // special GENERATED value is returned instead
    public static SourceLocation resolveSourceLocation() {
        if (!currentLocation.isEmpty()) {
            return currentLocation.peek();
        }
        else {
            return GENERATED;
        }
    }

    public boolean isGenerated() {
        return this == GENERATED;
    }
}