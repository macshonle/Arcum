package edu.ucsd.arcum.ui.views;

import org.eclipse.jface.viewers.TreeViewer;

abstract public class EntityTableElement
{
    public Object[] getChildren() {
        return new Object[0];
    }

    public boolean hasChildren() {
        return false;
    }

    public Object getParent() {
        return null;
    }

    abstract public String getColumnText(int columnIndex);

    abstract public void handleDoubleClick(TreeViewer treeViewer);
}