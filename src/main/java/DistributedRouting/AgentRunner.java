package DistributedRouting;

import DistributedRouting.grpc.*;
import DistributedRouting.util.Constants;
import DistributedRouting.util.Logging;
import io.grpc.*;

import java.util.concurrent.CountDownLatch;

/**
 * An Agent in the graph. The agent is able to send and receive messages from
 * its neighbors.
 */
public class AgentRunner implements Runnable {

    private AgentCore core;
    private LogGrpc.LogBlockingStub graphStub;
    private final int port;
    private final int id;

    private CountDownLatch countdown;

    public AgentRunner(Integer id, Integer startingVertex, Integer destination, CountDownLatch countdown) {
        Logging.logService("Starting agent " + id);
        this.core = new AgentCore(id, startingVertex, destination);
        this.port = Constants.MESSAGE_PORT + id;
        this.id = id;
        this.countdown = countdown;

        // Connect to logger
        String target = String.format("localhost:%d", Constants.MESSAGE_PORT);
        ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
                .build();
        graphStub = LogGrpc.newBlockingStub(channel);

    }

    /**
     * Main loop of AgentRunner. This method regularly checks if a message has been
     * received and, if it has, sends off another message to all of its neighbors
     * except the one from which the message was received.
     */
    public void run() {

        int messageLimit = 2000;
        int currMessages = 0;
        try {
            while (true) {
                if (id == 1) {
                    Logging.logService("Iteration: " + currMessages);
                }
                Thread.sleep(50);

                int previousVertex = core.getCurrentVertex();
                NeighborsReply reply = graphStub.getNeighbors(NeighborsRequest.newBuilder()
                        .setPrevNode(core.getPreviousVertex())
                        .setNodeId(previousVertex).build());
                core.traverse(reply.getEdgesList());

                graphStub.sendLog(MessageLog.newBuilder()
                                .setAntId(id)
                                .setPrevVertex(previousVertex)
                                .setNextVertex(core.getCurrentVertex())
                                .setModifier(Float.valueOf(core.getModifier())).build());

                if (currMessages != 0 && currMessages % 100 == 0) {
                    graphStub.saveGraph(SaveGraphRequest.newBuilder().setIteration(currMessages).build());
                }

                if (currMessages++ == messageLimit) break;
            }
        } catch (Exception ex) {
            Logging.logError("Encountered error in agent " + id + " in main loop: " + ex.getMessage());
        }
        countdown.countDown();
    }
}
