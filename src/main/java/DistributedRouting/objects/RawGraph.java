package DistributedRouting.objects;

import DistributedRouting.util.Logging;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * Simple interface to define a Graph. Graphs have vertices and edges and are
 * able to answer queries like "what are neighbors of vetex 1?".
 */
public class RawGraph {
    private List<Integer> vertices;
    private Map<Integer, Set<Integer>> edges;
    private int source;
    private int dest;

    public RawGraph(int source, int dest, List<Integer> vertices, Map<Integer, Set<Integer>> edges) {
        this.vertices = vertices;
        this.edges = edges;
        this.source = source;
        this.dest = dest;
    }

    public int getSource() {
         return source;
    }

    public int getDest() {
        return dest;
    }

    public List<Integer> getVertices() {
        return vertices;
    }

    public Map<Integer, Set<Integer>> getEdges() {
        return edges;
    }

    /**
     * Get all neighbors of a given vertex.
     *
     * @param vertex    Vertex to fetch neighbors of.
     * @return          The neighbors of the provided vertex
     */
    public Set<Integer> neighborsOf(int vertex) {
        return edges.get(vertex);
    }

    public RawGraph asUndirectedGraph() {
        // Make mutable
        Map<Integer, Set<Integer>> newEdges = new HashMap<>();

        for (Integer source : edges.keySet()) {
            Set<Integer> destinations = new HashSet<>(edges.get(source));
            newEdges.merge(source, destinations, Sets::union);
            for (Integer dest : destinations) {
                Set<Integer> dests = newEdges.compute(dest, (k,v) -> (v == null) ? new HashSet<>() : v);
                dests.add(source);
            }
        }
        return new RawGraph(source, dest, new ArrayList<>(vertices), newEdges);
    }
}
