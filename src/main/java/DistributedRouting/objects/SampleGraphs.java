package DistributedRouting.objects;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Sample graphs from which the network topology can be derived.
 */
public class SampleGraphs {

    /**
     * K3 graph.
     */
    public static RawGraph simpleGraph = new RawGraph(1, 1, Arrays.asList(1,2,3), ImmutableMap.of(
            1, Sets.newHashSet(2),
            2, Sets.newHashSet(3),
            3, Sets.newHashSet(1)));

    public static RawGraph doublePath = new RawGraph(1, 6,
            IntStream.rangeClosed(1,6).boxed().toList(),
            ImmutableMap.of(1, ImmutableSet.of(2,4),2, ImmutableSet.of(3), 3, ImmutableSet.of(6),
                    4, ImmutableSet.of(5), 5, ImmutableSet.of(6))
        );

    public static RawGraph unevenPath(int length1, int length2) {
        List<Integer> vertices = IntStream.rangeClosed(1, length1 + length2).boxed().toList();
        HashMap<Integer, Set<Integer>> edges = new HashMap<>();
        edges.put(1, ImmutableSet.of(length1 + 1, 2));
        for (int i = 2; i < length1; i++) {
            edges.put(i, ImmutableSet.of(i+1));
        }
        for (int i = 2; i < length2; i++) {
            edges.put(i + (length1 -1), ImmutableSet.of(i+length1));
        }
        edges.put(length1, ImmutableSet.of(length1 + length2));
        edges.put(length1 + length2 - 1, ImmutableSet.of(length1 + length2));

        return new RawGraph(1, length1 + length2, vertices, edges);
    }

    public static RawGraph longDoublePath(int length) {
        List<Integer> vertices = IntStream.rangeClosed(1, length*2).boxed().toList();
        HashMap<Integer, Set<Integer>> edges = new HashMap<>();
        edges.put(1, ImmutableSet.of(length + 1, 2));
        for (int i = 2; i < length; i++) {
            edges.put(i, ImmutableSet.of(i+1));
            edges.put(i + (length - 1), ImmutableSet.of(i+length));
        }
        edges.put(length, ImmutableSet.of(length * 2));
        edges.put(2*length - 1, ImmutableSet.of(length * 2));

        return new RawGraph(1, length*2, vertices, edges);
    }

    public static RawGraph erdosReyniGraph(int n, float p, Random random) {
        List<Integer> vertices = IntStream.rangeClosed(1, n).boxed().toList();
        HashMap<Integer, Set<Integer>> edges = new HashMap<>();
        for (int i = 1; i < n; i++) {
            Set<Integer> currentEdges = new HashSet<>();
            for (int j = i+1; j <= n; j++) {
                if (random.nextFloat(0,1) > p) continue;
                currentEdges.add(j);
            }
            edges.put(i, currentEdges);
        }
        return new RawGraph(1, n, vertices, edges);
    }
}
