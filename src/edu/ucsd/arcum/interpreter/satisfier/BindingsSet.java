package edu.ucsd.arcum.interpreter.satisfier;

import static edu.ucsd.arcum.ArcumPlugin.DEBUG;

import java.util.*;

import edu.ucsd.arcum.interpreter.ast.TraitSignature;
import edu.ucsd.arcum.interpreter.query.EntityTuple;
import edu.ucsd.arcum.util.StringUtil;

public class BindingsSet implements Iterable<BindingMap>
{
    private final SortedSet<BindingMap> set;

    private BindingsSet() {
        this.set = new TreeSet<BindingMap>();
    }

    public BindingsSet union(BindingsSet that) {
        BindingsSet result = newEmptySet();
        result.set.addAll(this.set);
        result.set.addAll(that.set);
        return result;
    }

    public static BindingsSet newEmptySet() {
        return new BindingsSet();
    }

    public static BindingsSet newSet(BindingMap... maps) {
        BindingsSet result = new BindingsSet();
        for (BindingMap map : maps) {
            result.addEntry(map);
        }
        return result;
    }

    public void addEntry(BindingMap theta) {
        set.add(theta);
    }

    @Override
    public String toString() {
        return String.format("{%s} = %d members", StringUtil.separate(this.set, String
            .format(";%n")), this.set.size());
    }

    public Collection<List<EntityTuple>> extractAsEntityTuples(List<TraitSignature> types)
    {
        List<List<EntityTuple>> result = new ArrayList<List<EntityTuple>>(set.size());
        for (BindingMap bindingMap : set) {
            List<EntityTuple> entityTuple = bindingMap.asEntityTuple(types);
            result.add(entityTuple);
        }
        if (DEBUG) {
            System.out.printf("Turned:%n%s%nInto:%n%s%n%n", this, StringUtil.separate(
                result, String.format("%n")));
        }
        return result;
    }

    public int size() {
        return set.size();
    }

    @Override
    public Iterator<BindingMap> iterator() {
        return set.iterator();
    }

    public boolean isEquivalentTo(BindingsSet that) {
        return this.set.equals(that.set);
    }
}
