package me.gregorias.ghoul.interfaces.rest;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import me.gregorias.ghoul.kademlia.KademliaRouting;
import me.gregorias.ghoul.kademlia.KademliaStore;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST interface for kademlia. See resources in this package for available commands.
 *
 * @author Grzegorz Milka
 */
public class RESTApp {
  private static final Logger LOGGER = LoggerFactory.getLogger(RESTApp.class);
  private final KademliaRouting mKademlia;
  private final KademliaStore mStore;
  private final URI mUri;
  private final Lock mLock;
  private final Condition mShutDownCondition;
  private final AtomicBoolean mHasShutDownBeenCalled;

  public RESTApp(KademliaRouting kademlia, KademliaStore store, URI uri) {
    mKademlia = kademlia;
    mStore = store;
    mUri = uri;
    mLock = new ReentrantLock();
    mShutDownCondition = mLock.newCondition();
    mHasShutDownBeenCalled = new AtomicBoolean(false);
  }

  public void run() {
    LOGGER.info("run()");
    ResourceConfig config = createConfig();
    final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(mUri, config);
    try {
      server.start();
      mLock.lock();
      try {
        while (!mHasShutDownBeenCalled.get()) {
          mShutDownCondition.await();
        }
        server.shutdown();
      } catch (InterruptedException e) {
        server.shutdownNow();
        LOGGER.error("run() -> Unexpected interrupt exception.", e);
        return;
      } finally {
        mLock.unlock();
      }
    } catch (IOException e) {
      LOGGER.error("run() -> IOException.", e);
    }
    LOGGER.info("run() -> void");
  }

  private ResourceConfig createConfig() {
    ResourceConfig config = new ResourceConfig();
    config.register(new KademliaStartResource(mKademlia, mStore));
    config.register(new GetLocalKeyResource(mKademlia));
    config.register(new FindNodesResource(mKademlia));
    config.register(new KademliaGetRoutingTableResource(mKademlia));
    config.register(new KademliaStopResource(mKademlia, mStore));
    config.register(new ServerShutDownResource(mLock, mShutDownCondition, mHasShutDownBeenCalled));
    config.register(new KademliaGetResource(mStore));
    config.register(new KademliaPutResource(mStore));
    return config;
  }

}
