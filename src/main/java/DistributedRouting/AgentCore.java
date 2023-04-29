package DistributedRouting;

import DistributedRouting.grpc.Edge;
import DistributedRouting.util.Constants;
import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AgentCore {

    private int id;
    private int currentVertex;

    private int previousVertex;

    private int timestep;
    private int lastSeenSource;
    private float modifier;

    private int destination;

    private static final int delta = 15;

    private Edge pickUsingPheromones(List<Edge> edges) {
        List<Pair<Edge, Double>> weightsList = IntStream.range(0, edges.size())
                .mapToObj(i -> new Pair<Edge, Double>(edges.get(i), (double) edges.get(i).getPheromoneLevel()))
                .collect(Collectors.toList());
        return new EnumeratedDistribution<>(weightsList).sample();
    }

    public AgentCore(int id, int startingVertex, int destination) {
        this.id = id;
        this.currentVertex = startingVertex;
        this.previousVertex = -1;
        this.destination = destination;
        this.modifier = 1;
        this.lastSeenSource = (startingVertex == 1) ? 1 : delta + 1;
        this.timestep = 1;
    }

    public Edge traverse(List<Edge> edges) {
        timestep++;
        if (modifier > 1) {
            modifier -= 0.2;
        }
        Edge edge = pickUsingPheromones(edges);
        previousVertex = currentVertex;
        currentVertex = edge.getNodeId();

        if (currentVertex == destination && timestep - lastSeenSource < delta) {
            modifier = 2;
        } else if (currentVertex == 1) {
            lastSeenSource = timestep;
            modifier = 1;
        }

        return edge;
    }

    public float getModifier() {
        return modifier;
    }

    public int getPreviousVertex() {
        return previousVertex;
    }

    public int getCurrentVertex() {
        return currentVertex;
    }
}
