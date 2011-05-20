package edu.ucsd.arcum.ui.views;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jface.viewers.TreeViewer;

import edu.ucsd.arcum.exceptions.SourceLocation;
import edu.ucsd.arcum.interpreter.query.Entity;

public abstract class EntityTableSubElement extends EntityTableElement
{
    protected EntityTableFolder parent;
    protected ASTNode node;
    private SourceLocation location;

    public EntityTableSubElement(EntityTableFolder parent, ASTNode node) {
        this.parent = parent;
        this.node = node;
        if (node != null) {
            this.location = new SourceLocation(node);
        }
        else {
            this.location = new SourceLocation();
        }
    }

    @Override
    public EntityTableFolder getParent() {
        return parent;
    }

    @Override
    public void handleDoubleClick(TreeViewer treeViewer) {
        location.openInEditor();
    }

    @Override
    public final String getColumnText(int columnIndex) {
        switch (columnIndex) {
        case 0:
            return getNameColumnContents();
        case 1:
            if (node != null)
                return Entity.getDisplayString(node);
            else
                return this.toString();
        case 2:
            return location.getFileName();
        case 3:
            return location.getPathName();
        case 4:
            return location.getLineAsString();
        default:
            return "-";
        }
    }

    protected abstract String getNameColumnContents();

    protected String getPathName() {
        return location.getPathName();
    }

}
