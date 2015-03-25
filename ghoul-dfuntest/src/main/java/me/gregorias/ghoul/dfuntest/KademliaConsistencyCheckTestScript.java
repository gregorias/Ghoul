package me.gregorias.ghoul.dfuntest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.gregorias.dfuntest.TestResult;
import me.gregorias.dfuntest.TestScript;
import me.gregorias.dfuntest.TestResult.Type;
import me.gregorias.ghoul.kademlia.KademliaException;
import me.gregorias.ghoul.kademlia.Key;
import me.gregorias.ghoul.kademlia.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test Script which runs all kademlia and periodically checks whether their routing tables form a
 * a strongly connected graph.
 *
 * This test fails iff at least one check gives more than one component or an IOException happens.
 */
public class KademliaConsistencyCheckTestScript implements TestScript<KademliaApp> {
  private static final Logger LOGGER = LoggerFactory
      .getLogger(KademliaConsistencyCheckTestScript.class);
  private static final int START_UP_DELAY = 5;
  private static final TimeUnit START_UP_DELAY_UNIT = TimeUnit.SECONDS;
  private static final long CHECK_DELAY = 20;
  private static final TimeUnit CHECK_DELAY_UNIT = TimeUnit.SECONDS;

  /**
   * How many times consistency check should be run.
   */
  private static final int CHECK_COUNT = 5;

  private final ScheduledExecutorService mScheduledExecutor;
  private final AtomicBoolean mIsFinished;
  private TestResult mResult;

  public KademliaConsistencyCheckTestScript(ScheduledExecutorService scheduledExecutor) {
    mScheduledExecutor = scheduledExecutor;
    mIsFinished = new AtomicBoolean(false);
  }

  @Override
  public TestResult run(Collection<KademliaApp> apps) {
    LOGGER.info("run(): Running consistency test on {} hosts.", apps.size());

    try {
      startUpApps(apps);
      try {
        Thread.sleep(START_UP_DELAY_UNIT.toMillis(START_UP_DELAY));
      } catch (InterruptedException e) {
        LOGGER.warn("Unexpected interrupt.");
      }
    } catch (IOException e) {
      return new TestResult(Type.FAILURE, "Could not start up applications due to: "
        + e.getMessage() + ".");
    }

    LOGGER.info("run(): Starting kademlias.");
    try {
      startKademlias(apps);
    } catch (KademliaException | IOException e) {
      shutDownApps(apps);
      return new TestResult(Type.FAILURE, "Could not start kademlias due to: " + e.getMessage()
        + ".");
    }

    mResult = new TestResult(Type.SUCCESS, "Connection graph was consistent the entire time.");

    ConsistencyChecker consistencyChecker = new ConsistencyChecker(apps);

    LOGGER.info("run(): Scheduling consistency checker.");
    mScheduledExecutor.scheduleWithFixedDelay(consistencyChecker, CHECK_DELAY, CHECK_DELAY,
        CHECK_DELAY_UNIT);

    synchronized (this) {
      while (!mIsFinished.get()) {
        try {
          this.wait();
        } catch (InterruptedException e) {
          LOGGER.warn("Unexpected interrupt in kademlia test script.");
          Thread.currentThread().interrupt();
        }
      }
    }

    stopKademlias(apps);
    shutDownApps(apps);
    LOGGER.info("run() -> {}", mResult);
    return mResult;
  }

  @Override
  public String toString() {
    return "KademliaConsistencyCheckTestScript";
  }

  /**
   * Scheduled runnable which checks whether connection graph is strongly connected.
   * It shuts itself down after CHECK_COUNT tries or after failed test.
   * On shut down it informs the main test thread about it.
   */
  private class ConsistencyChecker implements Runnable {
    private final Collection<KademliaApp> mApps;
    private int mCheckCount;

    public ConsistencyChecker(Collection<KademliaApp> apps) {
      mApps = apps;
      mCheckCount = CHECK_COUNT;
    }

    @Override
    public void run() {
      LOGGER.info("ConsistencyChecker.run()");
      Map<Key, Collection<Key>> graph;
      try {
        LOGGER.trace("ConsistencyChecker.run(): getConnectionGraph()");
        graph = getConnectionGraph(mApps);
        LOGGER.trace("ConsistencyChecker.run(): checkConsistency(graph), graph: \n{}",
            graphToString(graph));
        ConsistencyResult result = checkConsistency(graph);
        if (result.getType() == ConsistencyResult.Type.INCONSISTENT) {
          mResult = new TestResult(Type.FAILURE, "Found inconsistent graph.\n"
              + result.getInfo());
          shutDown();
          LOGGER.info("ConsistencyChecker.run() -> failure");
          return;
        }
      } catch (IOException e) {
        mResult = new TestResult(Type.FAILURE, "Could not get connection graph: " + e + ".");
        shutDown();
        LOGGER.info("ConsistencyChecker.run() -> failure");
        return;
      }

      --mCheckCount;
      if (mCheckCount == 0) {
        shutDown();
      }

      LOGGER.info("ConsistencyChecker.run() -> success");
    }

    private void shutDown() {
      mScheduledExecutor.shutdown();

      synchronized (KademliaConsistencyCheckTestScript.this) {
        LOGGER.info("ConsistencyChecker.run(): Notifying that this is the last test.");
        mIsFinished.set(true);
        KademliaConsistencyCheckTestScript.this.notifyAll();
      }

    }
  }

  private static class ConsistencyResult {
    private final Type mType;
    private final String mInfo;

    public static enum Type {
      CONSISTENT, INCONSISTENT
    }

    public ConsistencyResult(Type type, String info) {
      mType = type;
      mInfo = info;
    }

    public String getInfo() {
      return mInfo;
    }

    public Type getType() {
      return mType;
    }
  }

  private static ConsistencyResult checkConsistency(Map<Key, Collection<Key>> graph) {
    Collection<Collection<Key>> components = findStronglyConnectedComponents(graph);
    if (components.size() > 1) {
      StringBuilder informationString = new StringBuilder();
      informationString.append("The connection graph was:\n");
      informationString.append(graphToString(graph));
      informationString.append("\nIt's strongly connected components are:\n");
      for (Collection<Key> component : components) {
        informationString.append(collectionToString(component));
        informationString.append("\n");
      }
      return new ConsistencyResult(ConsistencyResult.Type.INCONSISTENT,
          informationString.toString());
    } else {
      return new ConsistencyResult(ConsistencyResult.Type.CONSISTENT, "");
    }
  }

  private static Map<Key, Collection<Key>> getConnectionGraph(Collection<KademliaApp> apps) throws
      IOException {
    LOGGER.trace("getConnectionGraph({})", apps.size());
    Map<Key, Collection<Key>> graph = new HashMap<>();
    for (KademliaApp app : apps) {
      Key key = app.getKey();
      Collection<Key> connectedApps = new LinkedList<>();
      LOGGER.trace("getConnectionGraph(): app[{}].getRoutingTable()", app.getId());
      Collection<NodeInfo> nodeInfos = app.getRoutingTable();
      for (NodeInfo info : nodeInfos) {
        connectedApps.add(info.getKey());
      }
      graph.put(key, connectedApps);
    }

    return graph;
  }

  private static void startUpApps(Collection<KademliaApp> apps) throws IOException {
    Collection<KademliaApp> startedApps = new LinkedList<>();
    for (KademliaApp app : apps) {
      try {
        app.startUp();
        startedApps.add(app);
      } catch (IOException e) {
        LOGGER.error("runApps() -> Could not run kademlia application: {}", app.getName(), e);
        for (KademliaApp startApp : startedApps) {
          try {
            startApp.shutDown();
          } catch (IOException e1) {
            LOGGER.error("runApps() -> Could not shutdown kademlia application: {}.",
                startApp.getName(), e);
          }
        }
        throw e;
      }
    }
  }

  private static void startKademlias(Collection<KademliaApp> kademlias) throws
      KademliaException,
      IOException {
    Collection<KademliaApp> runApps = new LinkedList<>();
    for (KademliaApp kademlia : kademlias) {
      try {
        kademlia.start();
        runApps.add(kademlia);
      } catch (IOException | KademliaException e) {
        LOGGER.error("startKademlias() -> Could not start kademlia application: {}",
            kademlia.getName(), e);
        for (KademliaApp appToStop : runApps) {
          try {
            appToStop.shutDown();
          } catch (IOException e1) {
            LOGGER.error("startKademlias() -> Could not shutdown kademlia application: {}.",
                appToStop.getName(), e);
          }
        }
        throw e;
      }
    }
  }

  private static void stopKademlias(Collection<KademliaApp> kademlias) {
    for (KademliaApp kademlia : kademlias) {
      try {
        kademlia.stop();
      } catch (IOException | KademliaException e) {
        LOGGER.error("stopApps() -> Could not shutdown kademlia application: {}",
            kademlia.getName(), e);
      }
    }
  }

  private static void shutDownApps(Collection<KademliaApp> apps) {
    for (KademliaApp app : apps) {
      try {
        app.shutDown();
      } catch (IOException e) {
        LOGGER.error("shutdownApps() -> Could not shutdown kademlia application: {}",
            app.getName(), e);
      }
    }
  }

  // Graph utilities
  private static String collectionToString(Collection<Key> collection) {
    StringBuilder collectionString = new StringBuilder();
    collectionString.append("[");
    boolean isFirst = true;
    for (Key key : collection) {
      if (!isFirst) {
        collectionString.append(", ");
      }
      collectionString.append(key.toInt().toString());
      isFirst = false;
    }
    collectionString.append("]");
    return collectionString.toString();
  }

  private static String graphToString(Map<Key, Collection<Key>> graph) {
    StringBuilder graphString = new StringBuilder();
    for (Map.Entry<Key, Collection<Key>> entry : graph.entrySet()) {
      graphString.append(entry.getKey().toInt().toString() + ": ");
      graphString.append(collectionToString(entry.getValue()));
      graphString.append("\n");
    }
    return graphString.toString();
  }

  /**
   * Find strongly connected components of the graph using Kosaraju's algorithm.
   *
   * @param graph graph
   * @return strongly connected components
   */
  private static Collection<Collection<Key>> findStronglyConnectedComponents(
      Map<Key, Collection<Key>> graph) {
    LinkedList<Key> reverseExitOrderStack = new LinkedList<>();

    Set<Key> visitedVerts = new HashSet<>();

    // Performs DFS to fill reverseExitOrderStack
    for (Map.Entry<Key, Collection<Key>> entry : graph.entrySet()) {
      Key key = entry.getKey();
      if (visitedVerts.contains(key)) {
        continue;
      }

      LinkedList<Key> keyStack = new LinkedList<>();
      LinkedList<Queue<Key>> neighoursStack = new LinkedList<>();

      visitedVerts.add(key);
      keyStack.push(key);
      Queue<Key> neighboursQueue = new LinkedList<>(entry.getValue());
      neighoursStack.push(neighboursQueue);

      while (!keyStack.isEmpty()) {
        Key topKey = keyStack.getFirst();
        neighboursQueue = neighoursStack.getFirst();

        if (neighboursQueue.isEmpty()) {
          reverseExitOrderStack.push(topKey);
          neighoursStack.pop();
          keyStack.pop();
        } else {
          Key nextNeighbour = neighboursQueue.poll();
          if (!visitedVerts.contains(nextNeighbour)) {
            visitedVerts.add(nextNeighbour);
            keyStack.push(nextNeighbour);
            neighboursQueue = new LinkedList<>(graph.get(nextNeighbour));
            neighoursStack.push(neighboursQueue);
          }
        }
      }
    }

    Map<Key, Collection<Key>> reversedGraph = reverseGraph(graph);

    Collection<Collection<Key>> stronglyConnectedComponents = new ArrayList<>();

    visitedVerts.clear();
    while (!reverseExitOrderStack.isEmpty()) {
      Key topKey = reverseExitOrderStack.pop();
      if (visitedVerts.contains(topKey)) {
        continue;
      }

      Collection<Key> component = new ArrayList<>();

      Queue<Key> toVisit = new LinkedList<>();
      component.add(topKey);
      visitedVerts.add(topKey);
      toVisit.add(topKey);

      while (!toVisit.isEmpty()) {
        topKey = toVisit.poll();
        Collection<Key> neighbours = reversedGraph.get(topKey);
        for (Key neighbour : neighbours) {
          if (!visitedVerts.contains(neighbour)) {
            component.add(neighbour);
            visitedVerts.add(neighbour);
            toVisit.add(neighbour);
          }
        }
      }
      stronglyConnectedComponents.add(component);
    }

    return stronglyConnectedComponents;
  }

  private static Map<Key, Collection<Key>> reverseGraph(Map<Key, Collection<Key>> graph) {
    Map<Key, Collection<Key>> reversedGraph = new HashMap<>();
    for (Key key : graph.keySet()) {
      reversedGraph.put(key, new ArrayList<>());
    }

    for (Map.Entry<Key, Collection<Key>> entry : graph.entrySet()) {
      for (Key neighbour : entry.getValue()) {
        reversedGraph.get(neighbour).add(entry.getKey());
      }
    }

    return reversedGraph;
  }
}
