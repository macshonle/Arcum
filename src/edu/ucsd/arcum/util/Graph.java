package edu.ucsd.arcum.util;

import static edu.ucsd.arcum.util.Graph.Visited.NOT_VISITED;
import static edu.ucsd.arcum.util.Graph.Visited.VISITED;
import static edu.ucsd.arcum.util.Graph.Visited.VISIT_STARTED;

import java.util.*;
import java.util.Map.Entry;

import com.google.common.collect.Lists;

public class Graph<T>
{
    public interface NodeVisitor<T>
    {
        void visit(T node);
    }

    public interface TopSortVisitor<T> extends NodeVisitor<T>
    {
        void cycleFound(List<T> nodes);
    }

    public interface LayeredVisitor<T, E extends Throwable>
    {
        void visitLayer(List<T> layer) throws E;

        void cycleFound(List<T> cycle) throws E;
    }

    // adjacency list representation
    private HashMap<Node<T>, Set<Node<T>>> graph;
    private HashMap<T, Node<T>> nodes;

    public static <T> Graph<T> newGraph() {
        return new Graph<T>();
    }

    public Graph() {
        this.graph = new LinkedHashMap<Node<T>, Set<Node<T>>>();
        this.nodes = new HashMap<T, Node<T>>();
    }

    public void addNode(T value) {
        selectiveCreateNode(value);
    }

    public void addEdge(T u, T v) {
        Node<T> nodeU = selectiveCreateNode(u);
        Node<T> nodeV = selectiveCreateNode(v);
        addEdge(nodeU, nodeV);
    }

    private Node<T> selectiveCreateNode(T value) {
        if (!nodes.containsKey(value)) {
            Node<T> node = new Node<T>(value);
            nodes.put(value, node);
            createNode(node);
            return node;
        }
        else {
            return nodes.get(value);
        }
    }

    private void createNode(Node<T> u) {
        _addNode(u, false);
    }

    private void addEdge(Node<T> u, Node<T> v) {
        Set<Node<T>> set = _addNode(u, true);
        _addNode(v, false);
        set.add(v);
    }

    private Set<Node<T>> _addNode(Node<T> u, boolean lookup) {
        if (!graph.containsKey(u)) {
            Set<Node<T>> set = new LinkedHashSet<Node<T>>();
            graph.put(u, set);
            return set;
        }
        else if (lookup) {
            return graph.get(u);
        }
        else {
            return null;
        }
    }

    public Collection<T> depthFirstSearch(T start) {
        final Collection<T> list = Lists.newArrayList();
        depthFirstSearch(start, new NodeVisitor<T>() {
            @Override public void visit(T node) {
                list.add(node);
            }
        });
        return list;
    }

    public void depthFirstSearch(T start, NodeVisitor<T> visitor) {
        Node<T> startNode = beginTraversal(start);
        _dfs(startNode, visitor);
    }

    public Collection<T> breadthFirstSearch(T start) {
        final Collection<T> list = Lists.newArrayList();
        breadthFirstSearch(start, new NodeVisitor<T>() {
            @Override public void visit(T node) {
                list.add(node);
            }
        });
        return list;
    }

    public void breadthFirstSearch(T start, NodeVisitor<T> visitor) {
        Node<T> startNode = beginTraversal(start);
        markAndImmediatelyVisit(startNode, visitor);
        // LinkedLists are FIFO queues
        Queue<Node<T>> queue = new LinkedList<Node<T>>();
        queue.add(startNode);
        for (;;) {
            Node<T> current = queue.poll();
            if (current == null)
                break;
            Set<Node<T>> connectedTo = graph.get(current);
            for (Node<T> v : connectedTo) {
                if (v.getVisited() == NOT_VISITED) {
                    markAndImmediatelyVisit(v, visitor);
                    queue.add(v);
                }
            }
        }
    }

    public <E extends Throwable> void iterateOverTopologicalLayers(
        LayeredVisitor<T, E> layeredVisitor) throws E
    {
        markUnvisited();
        while (notAllVisited()) {
            Map<Node<T>, Integer> inwardCount = computeUnvisitedInwardCount();

            List<T> layer = Lists.newArrayList();
            List<Node<T>> layerNodes = Lists.newArrayList();
            for (Entry<Node<T>, Integer> entry : inwardCount.entrySet()) {
                Node<T> node = entry.getKey();
                Integer count = entry.getValue();
                if (count == 0 && node.getVisited() != VISITED) {
                    layer.add(node.getValue());
                    layerNodes.add(node);
                }
            }

            if (layer.size() == 0) {
                layeredVisitor.cycleFound(new ArrayList<T>());
                return;
            }

            layeredVisitor.visitLayer(layer);
            for (Node<T> layerNode : layerNodes) {
                layerNode.setVisited(VISITED);
            }
        }
    }

    private boolean notAllVisited() {
        for (Node n : graph.keySet()) {
            if (n.getVisited() != VISITED) {
                return true;
            }
        }
        return false;
    }

    private Map<Node<T>, Integer> computeUnvisitedInwardCount() {
        Map<Node<T>, Integer> result = new HashMap<Node<T>, Integer>();
        for (Node<T> node : graph.keySet()) {
            if (node.getVisited() != VISITED) {
                result.put(node, 0);
            }
        }
        nodeCounting: for (Entry<Node<T>, Set<Node<T>>> entry : graph.entrySet()) {
            Node<T> key = entry.getKey();
            if (key.getVisited() == VISITED) {
                continue nodeCounting;
            }
            Set<Node<T>> adjList = entry.getValue();
            for (Node<T> pointedTo : adjList) {
                if (pointedTo.getVisited() != VISITED) {
                    result.put(pointedTo, result.get(pointedTo) + 1);
                }
            }            
        }
        return result;
    }

    // visits all nodes in the graph in a topological order, if possible. If
    // the graph has cycles the traversal the cycleFound method will be invoked
    // on the visitor, without any nodes being visited
    public void topologicalSort(TopSortVisitor<T> visitor) {
        assert visitor != null;
        _topsort(visitor);
    }

    public List<T> getTrees() {
        AbstractCollection<Node<T>> roots = _topsort(null);
        if (roots == null) {
            return Lists.newArrayList();
        }
        else {
            ArrayList<T> result = new ArrayList<T>(roots.size());
            for (Node<T> root : roots) {
                result.add(root.getValue());
            }
            return result;
        }
    }

    @Override public String toString() {
        StringBuilder builder = new StringBuilder();
        for (Entry<Node<T>, Set<Node<T>>> entry : graph.entrySet()) {
            builder.append(entry.getKey().toString());
            builder.append(" => ");
            builder.append(StringUtil.separate(entry.getValue()));
            builder.append(String.format("%n"));
        }
        return builder.toString();
    }

    // if visitor is null then the topsort only checks if there are cycles
    // and returns all "root" nodes (i.e. nodes with no inward edges). If
    // visitor is non-null then the return result will be null and should be
    // ignored
    private AbstractCollection<Node<T>> _topsort(TopSortVisitor<T> visitor) {
        Map<Node<T>, Integer> inwardCount = computeUnvisitedInwardCount();

        markUnvisited();
        for (Node<T> node : graph.keySet()) {
            inwardCount.put(node, 0); // and also initialize our count list

            List<T> cycles = cycleCheckingVisit(node);
            if (cycles != null) {
                if (visitor != null) {
                    int i = cycles.lastIndexOf(cycles.get(0));
                    if (i == 0) {
                        i = cycles.size() - 1;
                    }
                    visitor.cycleFound(cycles.subList(0, i + 1));
                }
                return null;
            }
        }

        for (Set<Node<T>> adjList : graph.values()) {
            for (Node<T> pointedTo : adjList) {
                inwardCount.put(pointedTo, inwardCount.get(pointedTo) + 1);
            }
        }

        HashSet<Node<T>> zeroList = new HashSet<Node<T>>();
        for (Entry<Node<T>, Integer> entry : inwardCount.entrySet()) {
            if (entry.getValue() == 0) {
                zeroList.add(entry.getKey());
            }
        }
        if (visitor == null) {
            return zeroList;
        }

        List<T> toVisit = new ArrayList<T>(nodes.size());
        while (!zeroList.isEmpty()) {
            Node<T> node = zeroList.iterator().next();
            zeroList.remove(node);

            toVisit.add(node.getValue());
            Set<Node<T>> adjList = graph.get(node);
            for (Node<T> pointedTo : adjList) {
                int count = inwardCount.get(pointedTo) - 1;
                inwardCount.put(pointedTo, count);
                if (count == 0) {
                    zeroList.add(pointedTo);
                }
            }
        }
        for (T value : toVisit) {
            visitor.visit(value);
        }
        return null;
    }

    private List<T> cycleCheckingVisit(Node<T> node) {
        if (node.getVisited() == VISIT_STARTED) {
            List<T> result = new ArrayList<T>();
            result.add(node.getValue());
            return result;
        }

        if (node.getVisited() == VISITED)
            return null;

        node.setVisited(VISIT_STARTED);
        Set<Node<T>> adjList = graph.get(node);
        for (Node<T> pointedTo : adjList) {
            List<T> result = cycleCheckingVisit(pointedTo);
            if (result != null) {
                result.add(0, node.getValue());
                return result;
            }
        }
        node.setVisited(VISITED);
        return null;
    }

    private Node<T> beginTraversal(T start) {
        assumeInGraph(start);
        Node<T> startNode = nodes.get(start);
        markUnvisited();
        return startNode;
    }

    private void assumeInGraph(T start) {
        if (!nodes.containsKey(start)) {
            throw new IllegalArgumentException("Node is not in this graph");
        }
    }

    private void markUnvisited() {
        for (Node n : graph.keySet()) {
            n.setVisited(NOT_VISITED);
        }
    }

    private void _dfs(Node<T> node, NodeVisitor<T> visitor) {
        markAndImmediatelyVisit(node, visitor);
        Set<Node<T>> connectedTo = graph.get(node);
        for (Node<T> v : connectedTo) {
            if (v.getVisited() == NOT_VISITED) {
                _dfs(v, visitor);
            }
        }
    }

    private void markAndImmediatelyVisit(Node<T> node, NodeVisitor<T> visitor) {
        visitor.visit(node.getValue());
        node.setVisited(VISITED);
    }

    private static class Node<T>
    {
        private T value;
        private Visited visited = NOT_VISITED;

        public Node(T data) {
            this.value = data;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T data) {
            this.value = data;
        }

        public Visited getVisited() {
            return visited;
        }

        public void setVisited(Visited visited) {
            this.visited = visited;
        }

        @Override public final int hashCode() {
            return super.hashCode();
        }

        @Override public final boolean equals(Object o) {
            return super.equals(o);
        }

        @Override public String toString() {
            return String.valueOf(value);
        }
    }

    static enum Visited
    {
        NOT_VISITED, VISIT_STARTED, VISITED
    }
}
