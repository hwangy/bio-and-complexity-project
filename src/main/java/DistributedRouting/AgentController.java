package DistributedRouting;

import DistributedRouting.grpc.*;
import DistributedRouting.objects.RawGraph;
import DistributedRouting.objects.SampleGraphs;
import DistributedRouting.util.Constants;
import DistributedRouting.util.Logging;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSinkImages;
import org.graphstream.ui.spriteManager.Sprite;
import org.graphstream.ui.spriteManager.SpriteManager;

public class AgentController {

    private static Map<String, Float> pheromoneMap = new HashMap<>();

    public static Server initializeListener(RawGraph graph, Graph graphVis, SpriteManager manager, Lock mapLock) throws Exception {
        Server server = Grpc.newServerBuilderForPort(Constants.MESSAGE_PORT, InsecureServerCredentials.create())
                .addService(new AgentLoggerImpl(graph, graphVis, manager, mapLock))
                .build()
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                interrupt();
                System.err.println("*** server shut down");
            }
        });
        return server;
    }

    /**
     * Return a color between red and green to visualize pheromone levels.
     * @param pheromoneValue    A float between 0 and 1
     * @return  A string of the form rgb(r,g,b)
     */
    private static String pheromoneToColor(Float pheromoneValue) {
        return String.format("rgb(%d,%d,0)", Math.round(255*(1-pheromoneValue)), Math.round(255*pheromoneValue));
    }

    private static String antNameForId(int id) {
        return String.format("ant_%d", id);
    }

    private static String edgeLabel(String start, String end) {
        if (start.compareTo(end) > 0) {
            return String.format("(%s,%s)", end, start);
        } else {
            return String.format("(%s,%s)", start, end);
        }
    }

    private static String edgeLabel(int start, int end) {
        return edgeLabel(String.valueOf(start), String.valueOf(end));
    }

    public static Graph drawGraph(RawGraph rawGraph) {
        Graph graph = new SingleGraph("Graph");
        graph.setAttribute("ui.stylesheet", """
                edge {
                    size: 2px;
                    fill-mode: dyn-plain;
                    fill-color: red, green;
                }
                
                node.terminal {
                    fill-color: blue;
                }""");
        for (Integer vertex : rawGraph.getVertices()) {
            graph.addNode(vertex.toString());
        }
        graph.getNode(String.valueOf(rawGraph.getSource())).setAttribute("ui.class", "terminal");
        graph.getNode(String.valueOf(rawGraph.getDest())).setAttribute("ui.class", "terminal");
        for (Map.Entry<Integer, Set<Integer>> entry : rawGraph.getEdges().entrySet()) {
            String s = entry.getKey().toString();
            for (Integer dest : entry.getValue()) {
                String d = dest.toString();
                String label = edgeLabel(s,d);
                graph.addEdge(label, s, d);
                pheromoneMap.put(label, Constants.INCREMENT);
                graph.getEdge(label).setAttribute("ui.color", 0);
                /*graph.getEdge(label).setAttribute(
                        "ui.style",
                        "fill-color: " + pheromoneToColor(0f) + ";");*/
            }
        }
        graph.display();
        return graph;
    }

    /**
     * Seeds
     *  5843648202025435093
     *  Small graph: 5924385651977311760
     * @param args
     */
    public static void main(String[] args) {
        int numVertices = 20;
        Scanner inputReader = new Scanner(System.in);
        System.out.println("Seed?");
        String seed = inputReader.nextLine();
        Random random;
        if (seed.isEmpty()) {
            random = new Random();
            Long currSeed = random.nextLong();
            random.setSeed(currSeed);
            System.out.println("Using seed: " + currSeed);
        } else {
            random = new Random(Long.valueOf(seed));
        }

        RawGraph graph = SampleGraphs.erdosReyniGraph(numVertices,0.2f, random);
        Graph graphVis = drawGraph(graph);
        SpriteManager manager = new SpriteManager(graphVis);
        Lock mapLock = new ReentrantLock();
        try {
            initializeListener(graph.asUndirectedGraph(), graphVis, manager, mapLock);
        } catch (Exception ex) {
            Logging.logError("Failed to start logging service");
            ex.printStackTrace();
        }

        // Wait to start
        inputReader.nextLine();

        int numAnts = 15;
        // We'll wait till all threads terminate
        CountDownLatch countdown = new CountDownLatch(numAnts);

        for (int i = 0; i < numAnts; i++) {
            // Start off the ants on vertex 1
            int startingVertex = 1;
            if (i > numAnts/2) {
                startingVertex = numVertices;
            }
            Thread agent = new Thread(new AgentRunner(i, startingVertex, numVertices, countdown));
            agent.start();

            // Give each ant a different color.
            manager.addSprite(antNameForId(i));
            int nextInt = random.nextInt(0xffffff + 1);
            String colorCode = String.format("#%06x", nextInt);

            graphVis.setAttribute("ui.stylesheet",
                    "sprite#" + antNameForId(i) + " { fill-color: " + colorCode + "; }");
        }

        new Thread(new Decay(pheromoneMap, mapLock, countdown)).start();

        try {
            countdown.await();
        } catch (InterruptedException ex) {
            Logging.logError("Failed to wait on threads!");
            ex.printStackTrace();
        }
    }

    private static class Decay implements Runnable {
        private final Map<String, Float> pheromoneMap;
        private Lock mapLock;

        private CountDownLatch latch;

        public Decay(Map<String, Float> pheromoneMap, Lock mapLock, CountDownLatch latch) {
            this.pheromoneMap = pheromoneMap;
            this.mapLock = mapLock;
            this.latch = latch;
        }
        @Override
        public void run() {
            while (true) {
                if (latch.getCount() == 0) {
                    return;
                }
                try {
                    Thread.sleep(100);
                    mapLock.tryLock(1, TimeUnit.SECONDS);
                    for (Map.Entry<String, Float> entry : pheromoneMap.entrySet()) {
                        entry.setValue(entry.getValue() * Constants.DECAY);
                    }
                } catch (InterruptedException ex) {
                    Logging.logError("Failed to acquire lock.");
                } finally {
                    mapLock.unlock();
                }
            }
        }
    }

    static class AgentLoggerImpl extends LogGrpc.LogImplBase {
        private Graph graphVis;
        private RawGraph graph;
        private SpriteManager manager;

        private Lock mapLock;

        private Lock lock;
        public AgentLoggerImpl(RawGraph graph, Graph graphVis, SpriteManager manager, Lock mapLock) {
            this.graph = graph;
            this.graphVis = graphVis;
            this.manager = manager;
            lock = new ReentrantLock();
            this.mapLock = mapLock;
        }

        @Override
        public void sendLog(MessageLog req, StreamObserver<StatusReply> responseObserver) {
            Sprite sprite = manager.getSprite(antNameForId(req.getAntId()));
            String label = edgeLabel(String.valueOf(req.getPrevVertex()), String.valueOf(req.getNextVertex()));

            // Set color based on pheremone level
            try {
                lock.tryLock(1, TimeUnit.SECONDS);
                mapLock.tryLock(1, TimeUnit.SECONDS);
                float increment = Constants.INCREMENT * req.getModifier();
                float pheromoneValue = pheromoneMap.computeIfPresent(
                        label, (k, v) -> (v <= 1 - increment) ? v + increment : v);
                /**
                 * Scale the pheromone value
                 */
                double visValue = Math.log(pheromoneValue*(Math.exp(1)-1)+1);
                graphVis.getEdge(label).setAttribute("ui.color", visValue);
                if (!sprite.attached() || !sprite.getAttachment().equals(graphVis.getEdge(label))) {
                    sprite.attachToEdge(label);
                    sprite.setPosition(0.5);
                }
            } catch (Exception ex) {
                Logging.logService("For ant " + req.getAntId() + " on " + label + ": " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                lock.unlock();
                mapLock.unlock();
            }
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void saveGraph(SaveGraphRequest req, StreamObserver<StatusReply> responseObserver) {
            /*FileSinkImages pic = new FileSinkImages(FileSinkImages.OutputType.png, FileSinkImages.Resolutions.HD720);
            pic.setLayoutPolicy(FileSinkImages.LayoutPolicy.COMPUTED_FULLY_AT_NEW_IMAGE);
            pic.setQuality(FileSinkImages.Quality.HIGH);
            try {
                pic.writeAll(graphVis, String.format("graph_frame_%d.png", req.getIteration()));
            } catch (IOException ex) {
                ex.printStackTrace();
            }*/
            graphVis.setAttribute("ui.screenshot", String.format("good_graph_frame_%d.png", req.getIteration()));
            responseObserver.onNext(StatusReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void getNeighbors(NeighborsRequest req, StreamObserver<NeighborsReply> responseObserver) {
            Set<Integer> neighbors = new HashSet<>(graph.neighborsOf(req.getNodeId()));
            if (neighbors.size() != 1 && req.getNodeId() != graph.getDest() && req.getNodeId() != graph.getSource()) {
                neighbors.remove(req.getPrevNode());
            }
            List<Edge> edges = neighbors.stream().map(
                    id -> Edge.newBuilder().setNodeId(id).setPheromoneLevel(
                            pheromoneMap.get(edgeLabel(req.getNodeId(), id))
                    ).build()).toList();
            responseObserver.onNext(NeighborsReply.newBuilder()
                    .addAllEdges(edges).build());
            responseObserver.onCompleted();
        }
    }
}
