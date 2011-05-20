package edu.ucsd.arcum.ui.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.TreeViewer;

public class EntityTableFolder extends EntityTableElement implements
        Comparable<EntityTableFolder>
{
    private String traitName;
    private boolean isSingleton;
    private boolean isStatic;
    private List<EntityTableElement> elements;

    public EntityTableFolder(String traitName, boolean isSingleton, boolean isStatic) {
        this.traitName = traitName;
        this.isSingleton = isSingleton;
        this.isStatic = isStatic;
        this.elements = new ArrayList<EntityTableElement>();
    }

    public String getTraitName() {
        return traitName;
    }

    // Lexically compares:
    //  * singletons before non-singletons
    //  * non-statics before statics
    //  * trait names alphabetically
    @Override
    public int compareTo(EntityTableFolder that) {
        if (this.isSingleton != that.isSingleton) {
            return (this.isSingleton) ? -1 : 1;
        }
        if (this.isStatic != that.isStatic) {
            return (this.isStatic) ? 1 : -1;
        }
        return traitName.compareTo(that.traitName);
    }

    @Override
    public boolean hasChildren() {
        return elements.size() > 0;
    }

    @Override
    public Object[] getChildren() {
        return elements.toArray();
    }

    public void addElements(List<? extends EntityTableElement> elements) {
        this.elements.addAll(elements);
    }

    public void addElement(EntityTableElement element) {
        this.elements.add(element);
    }

    @Override
    public String getColumnText(int columnIndex) {
        switch (columnIndex) {
        case 0:
            return getTraitName();
        case 1:
            if (elements.size() == 1) {
                return String.format("%d fragment matched", elements.size());
            }
            else {
                return String.format("%d fragments matched", elements.size());
            }
        default:
            return "";
        }
    }

    @Override
    public void handleDoubleClick(TreeViewer treeViewer) {
        boolean expanded = treeViewer.getExpandedState(this);
        treeViewer.setExpandedState(this, !expanded);
    }
}