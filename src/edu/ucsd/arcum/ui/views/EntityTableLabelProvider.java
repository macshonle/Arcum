package edu.ucsd.arcum.ui.views;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

public class EntityTableLabelProvider extends LabelProvider implements
        ITableLabelProvider
{
    public Image getColumnImage(Object element, int columnIndex) {
        return null;
    }

    public String getColumnText(Object element, int columnIndex) {
        if (element instanceof EntityTableElement) {
            EntityTableElement tableElement = (EntityTableElement)element;
            return tableElement.getColumnText(columnIndex);
        }
        else {
            return "####";
        }
    }
}