package edu.ucsd.arcum.ui.views;

import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

public class EntityTableDoubleClickListener implements IDoubleClickListener
{
    private TreeViewer treeViewer;

    public EntityTableDoubleClickListener(TreeViewer treeViewer) {
        this.treeViewer = treeViewer;
    }

    public void doubleClick(DoubleClickEvent event) {
        IStructuredSelection selection = (IStructuredSelection)event.getSelection();
        Object firstElement = selection.getFirstElement();
        if (firstElement instanceof EntityTableElement) {
            EntityTableElement element = (EntityTableElement)firstElement;
            element.handleDoubleClick(treeViewer);
        }
    }
}