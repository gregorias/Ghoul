package me.gregorias.ghoul.dfuntest;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import me.gregorias.dfuntest.TestResult;
import me.gregorias.dfuntest.TestScript;
import me.gregorias.dfuntest.TestResult.Type;
import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.data.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test Script which runs all kademlia and periodically checks whether their routing tables form a
 * a strongly connected graph.
 *
 * This test fails iff at least one check gives more than one component or an IOException happens.
 */
public class KademliaDataReplicationTestScript implements TestScript<KademliaApp> {
  private static final Logger LOGGER = LoggerFactory
      .getLogger(KademliaDataReplicationTestScript.class);
  private static final int START_UP_DELAY = 15;
  private static final TimeUnit START_UP_DELAY_UNIT = TimeUnit.SECONDS;
  private static final long CHECK_DELAY = 3;
  private static final TimeUnit CHECK_DELAY_UNIT = TimeUnit.SECONDS;

  private TestResult mResult;

  @Override
  public TestResult run(Collection<KademliaApp> apps) {
    LOGGER.info("run(): Running data replication test on {} hosts.", apps.size());

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

    mResult = new TestResult(Type.SUCCESS, "Test was successful");

    for (KademliaApp app : apps) {
      Key key = new Key(app.getEnvironment().getId());
      try {
        app.put(key, "HELLO");
        Thread.sleep(CHECK_DELAY_UNIT.toMillis(CHECK_DELAY));
        if (app.get(key) < 3) {
          mResult = new TestResult(Type.FAILURE, "Data didn't replicate.");
          break;
        }
      } catch (InterruptedException | IOException e) {
        e.printStackTrace();
        mResult = new TestResult(Type.FAILURE, "Got exception: " + e);
        break;
      }
    }

    stopKademlias(apps);
    shutDownApps(apps);
    LOGGER.info("run() -> {}", mResult);
    return mResult;
  }

  @Override
  public String toString() {
    return "KademliaDataReplicationTestScript";
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
}

