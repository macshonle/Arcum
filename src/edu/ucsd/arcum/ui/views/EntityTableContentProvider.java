package edu.ucsd.arcum.ui.views;

import static com.google.common.collect.Lists.newArrayList;
import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.*;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import edu.ucsd.arcum.interpreter.ast.MapTraitArgument;
import edu.ucsd.arcum.interpreter.ast.Option;
import edu.ucsd.arcum.interpreter.query.EntityTuple;
import edu.ucsd.arcum.interpreter.query.OptionMatchTable;
import edu.ucsd.arcum.interpreter.query.TraitValue;

public final class EntityTableContentProvider implements ITreeContentProvider
{
    private OptionMatchTable optionMatchTable;
    private Object[] uiElements;

    public void dispose() {
    // intentionally left blank
    }

    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    // intentionally left blank
    }

    public Object[] getElements(Object inputElement) {
        if (optionMatchTable == null) {
            return new Object[0];
        }
        if (uiElements == null) {
            List<EntityTableFolder> topLevel = computeElements();
            this.uiElements = topLevel.toArray();
        }
        return uiElements;
    }

    private List<EntityTableFolder> computeElements() {
        List<TraitValue> traits = newArrayList(optionMatchTable.getNonSingletons());
        Map<String, TraitValue> nested = removeNestedTraits(traits);

        final Collection<TraitValue> interfaceSingletons;
        final Collection<TraitValue> optionSingletons;

        interfaceSingletons = optionMatchTable.getInterfaceSingletons();
        optionSingletons = optionMatchTable.getOptionSingletons();

        Option option = optionMatchTable.getOption();
        String optionName = option.getName();
        String interfaceName = option.getOptionInterface().getName();
        
        List<EntityTableFolder> topLevel = new ArrayList<EntityTableFolder>();
        topLevel.add(makeSingletonFolder(optionName, optionSingletons, nested));
        topLevel.add(makeSingletonFolder(interfaceName, interfaceSingletons, nested));

        if (DEBUG) {
            System.out.printf("%d traits%n", traits.size());
        }
        for (TraitValue set : traits) {
            EntityTableFolder traitFolder = makeTraitFolder(set);
            topLevel.add(traitFolder);
        }
        Collections.sort(topLevel);
        if (DEBUG)
            System.out.printf("Returning %d elements%n", topLevel.size());
        return topLevel;
    }

    // Removes all nested traits from the given collection, returning a lookup table
    // for the removed elements
    private Map<String, TraitValue> removeNestedTraits(List<TraitValue> traits) {
        Map<String, TraitValue> nestedTraits = new HashMap<String, TraitValue>();

        for (Iterator<TraitValue> iter = traits.iterator(); iter.hasNext();) {
            TraitValue set = iter.next();
            if (set.isNested()) {
                nestedTraits.put(set.getTraitName(), set);
                iter.remove();
            }
        }
        return nestedTraits;
    }

    private EntityTableFolder makeSingletonFolder(String folderName,
        Collection<TraitValue> singletons, Map<String, TraitValue> nestedTraitLookup)
    {
        EntityTableFolder traitFolder = new EntityTableFolder(folderName, true, false);
        for (TraitValue set : singletons) {
            String traitName = set.getTraitName();
            boolean isStatic = set.isStatic();
            EntityTuple singleton = set.getSingleton();
            Map<String, Object> values = singleton.getValues();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String name = entry.getKey();
                Object entity = entry.getValue();
                EntityTableElement element;
                if (entity instanceof MapTraitArgument) {
                    element = makeTraitFolder(nestedTraitLookup.get(name));
                }
                else {
                    element = new EntityTableSingletonElement(name, entity, traitFolder);
                }
                traitFolder.addElement(element);
            }
        }
        return traitFolder;
    }

    private EntityTableFolder makeTraitFolder(TraitValue set) {
        List<EntityTuple> entities = set.getEntities();
        if (DEBUG)
            System.out.printf("%d entities%n", entities.size());
        String traitName = set.getTraitName();
        boolean isStatic = set.isStatic();
        EntityTableFolder traitFolder = new EntityTableFolder(traitName, false, isStatic);
        List<EntityTableTraitElement> allTraits = new ArrayList<EntityTableTraitElement>();
        for (EntityTuple tuple : entities) {
            EntityTableTraitElement trait = new EntityTableTraitElement(tuple,
                traitFolder);
            if (DEBUG)
                System.out.printf("Adding: %s%n", trait);
            allTraits.add(trait);
        }
        Collections.sort(allTraits);
        traitFolder.addElements(allTraits);
        return traitFolder;
    }

    public void setEntityTable(OptionMatchTable optionMatchTable) {
        this.optionMatchTable = optionMatchTable;
        this.uiElements = null;
    }

    @Override public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof EntityTableElement) {
            EntityTableElement parent = (EntityTableElement)parentElement;
            return parent.getChildren();
        }
        return new Object[0];
    }

    @Override public Object getParent(Object element) {
        if (element instanceof EntityTableElement) {
            EntityTableElement entityTableElement = (EntityTableElement)element;
            return entityTableElement.getParent();
        }
        return null;
    }

    @Override public boolean hasChildren(Object element) {
        if (element instanceof EntityTableElement) {
            EntityTableElement entityTableElement = (EntityTableElement)element;
            return entityTableElement.hasChildren();
        }
        return false;
    }
}