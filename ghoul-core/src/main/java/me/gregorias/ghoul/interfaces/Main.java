package me.gregorias.ghoul.interfaces;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.gregorias.ghoul.interfaces.rest.RESTApp;
import me.gregorias.ghoul.kademlia.BlockingMessageListener;
import me.gregorias.ghoul.kademlia.KademliaStore;
import me.gregorias.ghoul.kademlia.MessageListener;
import me.gregorias.ghoul.kademlia.MessageListeningServiceAdapter;
import me.gregorias.ghoul.kademlia.MessageSender;
import me.gregorias.ghoul.kademlia.MessageSenderAdapter;
import me.gregorias.ghoul.kademlia.data.GetDHTKeyMessage;
import me.gregorias.ghoul.kademlia.data.GetDHTKeyReplyMessage;
import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.KademliaRouting;
import me.gregorias.ghoul.kademlia.KademliaRoutingBuilder;
import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.kademlia.data.NodeInfo;
import me.gregorias.ghoul.network.ByteListeningService;
import me.gregorias.ghoul.network.ByteSender;
import me.gregorias.ghoul.network.NetworkAddressDiscovery;
import me.gregorias.ghoul.network.UserGivenNetworkAddressDiscovery;
import me.gregorias.ghoul.network.udp.UDPByteListeningService;
import me.gregorias.ghoul.network.udp.UDPByteSender;
import me.gregorias.ghoul.security.Certificate;
import me.gregorias.ghoul.security.CertificateImpl;
import me.gregorias.ghoul.security.CertificateStorage;
import me.gregorias.ghoul.security.CryptographyTools;
import me.gregorias.ghoul.security.GhoulProtocolException;
import me.gregorias.ghoul.security.KeyGenerator;
import me.gregorias.ghoul.security.PersonalCertificateManager;
import me.gregorias.ghoul.security.RegistrarClient;
import me.gregorias.ghoul.security.RegistrarDescription;
import me.gregorias.ghoul.security.SignedCertificate;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main access point to kademlia.
 *
 * This main starts up basic kademlia peer and sets up a REST interface.
 * It expects an XML configuration filename as an argument.
 *
 * XML configuration recognizes the following fields
 * <ul>
 * <li> localNetAddress - IP/host address of local host. Mandatory. </li>
 * <li> localNetPort - port to be used by local kademlia host. Mandatory. </li>
 * <li> bootstrapKey - the kademlia key of bootstrap host. Mandatory. </li>
 * <li> bootstrapNetAddress - IP/host address of boostrap host. Mandatory. </li>
 * <li> bootstrapNetPort - port used by bootstrap host. Mandatory. </li>
 * <li> localKey - key to be used by local kademlia host. Mandatory. </li>
 * <li> bucketSize - size of local kademlia bucket. Optional. </li>
 * <li> concurrencyFactor - The alpha parameter from the kademlia protocol. Optional. </li>
 * <li> heartBeatDelay - Delay between successive heart beats in seconds. Optional. </li>
 * <li> restPort - port of local REST interface. Mandatory. </li>
 * </ul>
 *
 * @see me.gregorias.ghoul.interfaces.rest.RESTApp
 *
 * @author Grzegorz Milka
 */
public class Main {
  public static final String XML_FIELD_LOCAL_ADDRESS = "localNetAddress";
  public static final String XML_FIELD_LOCAL_PORT = "localNetPort";
  public static final String XML_FIELD_BOOTSTRAP_KEY = "bootstrapKey";
  public static final String XML_FIELD_BOOTSTRAP_ADDRESS = "bootstrapNetAddress";
  public static final String XML_FIELD_BOOTSTRAP_PORT = "bootstrapNetPort";
  public static final String XML_FIELD_LOCAL_KEY = "localKey";
  public static final String XML_FIELD_BUCKET_SIZE = "bucketSize";
  public static final String XML_FIELD_CONCURRENCY_PARAMETER = "concurrencyParameter";
  public static final String XML_FIELD_HEART_BEAT_DELAY = "heartBeatDelay";
  public static final String XML_FIELD_REST_PORT = "restPort";
  public static final String XML_FIELD_USE_AND_ALLOW_SELF_SIGNED_CERTIFICATES =
      "useAndAllowSelfSignedCertificates";

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

    final InetAddress localInetAddress = InetAddress.getByName(config
        .getString(XML_FIELD_LOCAL_ADDRESS));
    final int localPort = config.getInt(XML_FIELD_LOCAL_PORT);
    final Key localKey = new Key(config.getInt(XML_FIELD_LOCAL_KEY));
    final URI baseURI = URI.create(String.format("http://%s:%s/", localInetAddress.getHostName(),
        config.getString(XML_FIELD_REST_PORT)));

    final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    final ExecutorService executor = Executors.newFixedThreadPool(3);

    Random random = new Random();
    KademliaRoutingBuilder builder = new KademliaRoutingBuilder(random);
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
    ByteSender byteSender = new UDPByteSender(datagramChannel);
    builder.setByteSender(byteSender);
    builder.setExecutor(scheduledExecutor);
    builder.setKey(localKey);

    if (config.containsKey(XML_FIELD_BUCKET_SIZE)) {
      final int bucketSize = config.getInt(XML_FIELD_BUCKET_SIZE);
      builder.setBucketSize(bucketSize);
    }

    if (config.containsKey(XML_FIELD_CONCURRENCY_PARAMETER)) {
      final int concurrencyParameter = config.getInt(XML_FIELD_CONCURRENCY_PARAMETER);
      builder.setConcurrencyParameter(concurrencyParameter);
    }

    if (config.containsKey(XML_FIELD_HEART_BEAT_DELAY)) {
      final long heartBeatDelay = config.getLong(XML_FIELD_HEART_BEAT_DELAY);
      builder.setHeartBeatDelay(heartBeatDelay, TimeUnit.MILLISECONDS);
    }

    NetworkAddressDiscovery networkAddressDiscovery = new UserGivenNetworkAddressDiscovery(
            new InetSocketAddress(localInetAddress, localPort));
    builder.setNetworkAddressDiscovery(networkAddressDiscovery);

    CryptographyTools tools = CryptographyTools.getDefault();
    KeyPair localKeyPair = KeyGenerator.generateKeys();

    builder.setPersonalKeyPair(localKeyPair);
    Optional<SecurityExtensions> securityExtensions = setUpSecurityExtensions(localKey,
        localKeyPair,
        tools,
        builder,
        config);

    if (securityExtensions.isPresent()) {
      boolean hasSetUp = setUpBootstrapHosts(
          localKey,
          localKeyPair,
          securityExtensions.get().mManager.getPersonalCertificates(),
          tools,
          networkAddressDiscovery,
          securityExtensions.get().mStorage,
          builder,
          config,
          byteSender,
          ubls);

      if (hasSetUp) {
        KademliaRouting kademlia = builder.createPeer();
        KademliaStore store = builder.createStore(kademlia);

        RESTApp app = new RESTApp(kademlia, store, baseURI);

        app.run();

        if (kademlia.isRunning()) {
          try {
            kademlia.stop();
          } catch (KademliaException e) {
            LOGGER.error("main(): kademlia.stop()", e);
          }
        }
      }
    } else {
      LOGGER.error("main(): Could not join the DHT");
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

  private static class SecurityExtensions {
    public final CertificateStorage mStorage;
    public final PersonalCertificateManager mManager;

    public SecurityExtensions(CertificateStorage storage, PersonalCertificateManager manager) {
      mStorage = storage;
      mManager = manager;
    }
  }

  private static Collection<SignedCertificate> joinDHT(Collection<RegistrarDescription> registrars,
                                                       KeyPair keyPair)
      throws IOException, ClassNotFoundException, GhoulProtocolException {
    RegistrarClient client = new RegistrarClient(
        registrars,
        keyPair,
        CryptographyTools.getDefault());
    LOGGER.info("joinDHT(): Joining the DHT.");
    return client.joinDHT();
  }

  private static boolean setUpBootstrapHosts(
      Key localKey,
      KeyPair localKeyPair,
      Collection<SignedCertificate> certificates,
      CryptographyTools tools,
      NetworkAddressDiscovery networkAddressDiscovery,
      CertificateStorage certificateStorage,
      KademliaRoutingBuilder builder,
      XMLConfiguration config,
      ByteSender byteSender,
      ByteListeningService byteListeningService) throws UnknownHostException {
    final InetAddress hostZeroInetAddress = InetAddress.getByName(
        config.getString(XML_FIELD_BOOTSTRAP_ADDRESS));
    final int hostZeroPort = config.getInt(XML_FIELD_BOOTSTRAP_PORT);
    final InetSocketAddress bootstrapAddress = new InetSocketAddress(hostZeroInetAddress,
        hostZeroPort);

    Collection<NodeInfo> peersWithKnownAddresses = new LinkedList<>();
    if (!config.containsKey(XML_FIELD_BOOTSTRAP_KEY)) {
      if (certificates.size() > 0) {
        localKey = certificates.iterator().next().getNodeDHTKey();
      }
      GetDHTKeyMessage requestMessage = new GetDHTKeyMessage(
          new NodeInfo(localKey, networkAddressDiscovery.getNetworkAddress()),
          new NodeInfo(localKey, bootstrapAddress),
          0,
          true,
          certificates);
      BlockingQueue<KademliaMessage> queue = new LinkedBlockingQueue<>();
      MessageListener messageListener = new BlockingMessageListener(queue);
      MessageListeningServiceAdapter messageListeningService =
          new MessageListeningServiceAdapter(byteListeningService);
      messageListeningService.registerListener(
          message -> message instanceof GetDHTKeyReplyMessage,
          messageListener);
      MessageSender sender = new MessageSenderAdapter(byteSender);
      sender.sendMessage(bootstrapAddress, requestMessage);
      GetDHTKeyReplyMessage msg;
      try {
        msg = (GetDHTKeyReplyMessage) queue.poll(20, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        return false;
      }
      if (msg == null) {
        return false;
      }
      peersWithKnownAddresses.add(new NodeInfo(msg.getKey(), bootstrapAddress));
      for (SignedCertificate certificate : msg.getCertificates()) {
        certificateStorage.addCertificate(certificate.getSignedObject());
      }
    } else {
      final Key bootstrapKey = new Key(config.getInt(XML_FIELD_BOOTSTRAP_KEY));
      if (!localKey.equals(bootstrapKey)) {
        peersWithKnownAddresses.add(new NodeInfo(bootstrapKey, bootstrapAddress));
      }
    }
    builder.setInitialPeersWithKeys(peersWithKnownAddresses);
    return true;
  }

  private static Optional<SecurityExtensions> setUpSecurityExtensions(
      Key localDHTKey,
      KeyPair localKeyPair,
      CryptographyTools tools,
      KademliaRoutingBuilder builder,
      HierarchicalConfiguration config) {
    PersonalCertificateManager personalManager;
    CertificateStorage storage;

    if (config.containsKey(Main.XML_FIELD_USE_AND_ALLOW_SELF_SIGNED_CERTIFICATES)) {
      Certificate personalCertificate = new CertificateImpl(localKeyPair.getPublic(),
          localDHTKey,
          localDHTKey,
          ZonedDateTime.now().plusDays(7));
      SignedCertificate signedCertificate = SignedCertificate.sign(personalCertificate,
          localKeyPair.getPrivate(),
          tools);

      Collection<SignedCertificate> certificates = new ArrayList<>();
      certificates.add(signedCertificate);

      personalManager = new PersonalCertificateManager(certificates);
      storage = new CertificateStorage(new HashMap<>(), tools, true);
    } else {
      HierarchicalConfiguration registrarsConfig = config.configurationAt(
          RegistrarMain.XML_FIELD_REGISTRARS_INFO);
      Collection<RegistrarDescription> registrars;
      Collection<SignedCertificate> personalCertificates;
      try {
        registrars = RegistrarMain.loadRegistrarDescriptions(registrarsConfig);
        personalCertificates = joinDHT(registrars, localKeyPair);
        personalManager = new PersonalCertificateManager(personalCertificates);
      } catch (ClassNotFoundException | GhoulProtocolException | IOException e) {
        LOGGER.error("setUpSecurityExtension(): Could not get certificates.", e);
        return Optional.empty();
      }

      if (personalCertificates.size() == 0) {
        return Optional.empty();
      }
      SignedCertificate certificate = personalCertificates.iterator().next();
      builder.setKey(certificate.getNodeDHTKey());

      Map<Key, PublicKey> issuerKeys = new HashMap<>();
      for (RegistrarDescription registrar : registrars) {
        issuerKeys.put(registrar.getKey(), registrar.getPublicKey());
      }
      storage = new CertificateStorage(issuerKeys, tools, false);
    }

    builder.setPersonalCertificateManager(personalManager);
    builder.setCertificateStorage(storage);
    return Optional.of(new SecurityExtensions(storage, personalManager));
  }
}
