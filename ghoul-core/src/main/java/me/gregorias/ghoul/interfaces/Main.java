package me.gregorias.ghoul.interfaces;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.DatagramChannel;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.gregorias.ghoul.interfaces.rest.RESTApp;
import me.gregorias.ghoul.kademlia.KademliaException;
import me.gregorias.ghoul.kademlia.KademliaRouting;
import me.gregorias.ghoul.kademlia.KademliaRoutingBuilder;
import me.gregorias.ghoul.kademlia.Key;
import me.gregorias.ghoul.kademlia.NodeInfo;
import me.gregorias.ghoul.network.udp.UDPByteListeningService;
import me.gregorias.ghoul.network.udp.UDPByteSender;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main access point to kademlia.
 *
 * This main starts up basic kademlia peer and sets up a REST interface.
 * It expects an XML configuration filename as an argument.
 *
 * XML configuration recognizes the following fields:
 * <ul>
 * <li> local-net-address - IP/host address of local host. </li>
 * <li> local-net-port - port to be used by local kademlia host. </li>
 * <li> bootstrap-key - the kademlia key of bootstrap host. </li>
 * <li> bootstrap-net-address - IP/host address of boostrap host. </li>
 * <li> bootstrap-net-port - port used by bootstrap host. </li>
 * <li> local-key - key to be used by local kademlia host. </li>
 * <li> bucket-size - size of local kademlia bucket. </li>
 * <li> rest-port - port of local REST interface. </li>
 * </ul>
 *
 * @see me.gregorias.ghoul.interfaces.rest.RESTApp
 *
 * @author Grzegorz Milka
 */
public class Main {
  public static final String XML_FIELD_LOCAL_ADDRESS = "local-net-address";
  public static final String XML_FIELD_LOCAL_PORT = "local-net-port";
  public static final String XML_FIELD_BOOTSTRAP_KEY = "bootstrap-key";
  public static final String XML_FIELD_BOOTSTRAP_ADDRESS = "bootstrap-net-address";
  public static final String XML_FIELD_BOOTSTRAP_PORT = "bootstrap-net-port";
  public static final String XML_FIELD_LOCAL_KEY = "local-key";
  public static final String XML_FIELD_BUCKET_SIZE = "bucket-size";
  public static final String XML_FIELD_REST_PORT = "rest-port";
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.out.println("Usage: Main CONFIG_FILE");
      return;
    }
    LOGGER.info("main({})", args[0]);
    String configFile = args[0];
    XMLConfiguration config;
    try {
      config = new XMLConfiguration(new File(configFile));
    } catch (ConfigurationException e) {
      LOGGER.error("main() -> Could not read configuration.", e);
      return;
    }

    XMLConfiguration kadConfig = config;
    final InetAddress localInetAddress = InetAddress.getByName(kadConfig
        .getString(XML_FIELD_LOCAL_ADDRESS));
    final int localPort = kadConfig.getInt(XML_FIELD_LOCAL_PORT);
    final InetAddress hostZeroInetAddress = InetAddress.getByName(kadConfig
        .getString(XML_FIELD_BOOTSTRAP_ADDRESS));
    final int hostZeroPort = kadConfig.getInt(XML_FIELD_BOOTSTRAP_PORT);
    final Key localKey = new Key(kadConfig.getInt(XML_FIELD_LOCAL_KEY));
    final Key bootstrapKey = new Key(kadConfig.getInt(XML_FIELD_BOOTSTRAP_KEY));
    final int bucketSize = kadConfig.getInt(XML_FIELD_BUCKET_SIZE);
    final URI baseURI = URI.create(String.format("http://%s:%s/", localInetAddress.getHostName(),
        kadConfig.getString(XML_FIELD_REST_PORT)));

    final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);
    final ExecutorService executor = Executors.newFixedThreadPool(2);

    KademliaRoutingBuilder builder = new KademliaRoutingBuilder(new Random());
    DatagramChannel datagramChannel = DatagramChannel.open();
    UDPByteListeningService ubls = new UDPByteListeningService(datagramChannel,
        localPort,
        executor);
    try {
      ubls.start();
    } catch (IOException e) {
      LOGGER.error("main() -> Could not create listening service.", e);
      return;
    }
    builder.setByteListeningService(ubls);
    builder.setByteSender(new UDPByteSender(datagramChannel));
    builder.setExecutor(scheduledExecutor);
    Collection<NodeInfo> peersWithKnownAddresses = new LinkedList<>();
    if (!localKey.equals(bootstrapKey)) {
      peersWithKnownAddresses.add(new NodeInfo(bootstrapKey, new InetSocketAddress(
          hostZeroInetAddress, hostZeroPort)));
    }
    builder.setInitialPeersWithKeys(peersWithKnownAddresses);
    builder.setKey(localKey);
    builder.setBucketSize(bucketSize);
    builder.setLocalAddress(new InetSocketAddress(localInetAddress, localPort));

    KademliaRouting kademlia = builder.createPeer();

    RESTApp app = new RESTApp(kademlia, baseURI);

    app.run();

    if (kademlia.isRunning()) {
      try {
        kademlia.stop();
      } catch (KademliaException e) {
        LOGGER.error("main(): kademlia.stop()", e);
      }
    }
    ubls.stop();
    try {
      LOGGER.debug("main(): executor.shutdown()");
      executor.shutdown();
      executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
      LOGGER.debug("main(): scheduledExecutor.shutdown()");
      scheduledExecutor.shutdown();
      scheduledExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOGGER.error("main() -> unexpected interrupt", e);
    }
    LOGGER.info("main() -> void");
  }
}