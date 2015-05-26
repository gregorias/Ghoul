package me.gregorias.ghoul.kademlia;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.gregorias.ghoul.kademlia.data.GetDHTKeyMessage;
import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.kademlia.data.NodeInfo;
import me.gregorias.ghoul.network.ByteListeningService;
import me.gregorias.ghoul.network.ByteSender;
import me.gregorias.ghoul.network.NetworkAddressDiscovery;
import me.gregorias.ghoul.security.CertificateStorage;
import me.gregorias.ghoul.security.CryptographyTools;
import me.gregorias.ghoul.security.PersonalCertificateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder of Kademlia routing peers and stores.
 *
 * If you want multiple kademlia peers on the same listening connection you have
 * to:
 * <ul>
 *   <li>Set {@link me.gregorias.ghoul.kademlia.ListeningService} only once.</li>
 *   <li>Use the same builder for all peers.</li>
 * </ul>
 *
 * This class is not thread safe.
 *
 * @author Grzegorz Milka
 */
public class KademliaRoutingBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaRoutingBuilder.class);
  private final Random mRandom;
  private static final int DEFAULT_BUCKET_SIZE = 10;
  private static final int DEFAULT_ALPHA = 5;
  private static final CryptographyTools CRYPTOGRAPHY_TOOLS = CryptographyTools.getDefault();

  private long mMessageTimeout = 5000;
  private TimeUnit mMessageTimeoutUnit = TimeUnit.MILLISECONDS;

  private long mHeartBeatDelay = 2;
  private TimeUnit mHeartBeatDelayUnit = TimeUnit.MINUTES;

  private ListeningService mListeningAdapter;

  private MessageSenderAdapter mMessageSender;
  private ScheduledExecutorService mExecutor;
  private int mBucketSize = DEFAULT_BUCKET_SIZE;
  private int mAlpha = DEFAULT_ALPHA;
  private Key mKey;
  private Collection<NodeInfo> mInitialPeersWithKeys = new LinkedList<>();
  private NetworkAddressDiscovery mNetworkAddressDiscovery;
  private PersonalCertificateManager mPersonalCertificateManager;
  private CertificateStorage mCertificateStorage;
  private KeyPair mPersonalKeyPair;

  public KademliaRoutingBuilder(Random random) {
    mRandom = random;
  }

  /**
   * Create inactive Kademlia store.
   *
   * @param routing KademliaRouting on which this store is based
   * @return Kademlia store with parameters set before.
   */
  public KademliaStore createStore(KademliaRouting routing) {
    LOGGER.info("createStore()");

    checkIfByteListeningServiceIsSet();
    checkIfByteSenderIsSet();
    checkIfExecutorIsSet();
    checkIfNetworkAddressDiscoveryIsSet();
    checkIfKeyPairIsSet();

    Key usedKey = getSetKeyOrCreateNew();

    ListeningService listeningService = new DemultiplexingMessageListeningService(usedKey,
        mListeningAdapter);

    LOGGER.debug("createStore() -> Key: {}", usedKey);
    Store store = new MemoryStore();
    return new KademliaStore(
        mNetworkAddressDiscovery,
        routing,
        mMessageSender,
        listeningService,
        store,
        mRandom);
  }

  /**
   * Create inactive Kademlia peer.
   *
   * @return Kademlia store with parameters set before.
   */
  public KademliaRouting createPeer() {
    LOGGER.info("createPeer()");

    checkIfByteListeningServiceIsSet();
    checkIfByteSenderIsSet();
    checkIfCertificateStorageIsSet();
    checkIfExecutorIsSet();
    checkIfNetworkAddressDiscoveryIsSet();
    checkIfPersonalCertificateManagerIsSet();

    Key usedKey = getSetKeyOrCreateNew();

    ListeningService listeningService = new DemultiplexingMessageListeningService(usedKey,
        mListeningAdapter);

    LOGGER.debug("createPeer() -> Key: {}", usedKey);
    return new KademliaRoutingImpl(
        mKey,
        mNetworkAddressDiscovery,
        mMessageSender,
        listeningService,
        mBucketSize,
        mAlpha,
        mInitialPeersWithKeys,
        mMessageTimeout,
        mMessageTimeoutUnit,
        mHeartBeatDelay,
        mHeartBeatDelayUnit,
        mPersonalCertificateManager,
        mCertificateStorage,
        mPersonalKeyPair,
        CRYPTOGRAPHY_TOOLS,
        mExecutor,
        mRandom);
  }

  /**
   * Set size of one bucket.
   *
   * @param bucketSize size of bucket
   * @return this
   */
  public KademliaRoutingBuilder setBucketSize(int bucketSize) {
    mBucketSize = bucketSize;
    return this;
  }

  /**
   * Set {@link me.gregorias.ghoul.network.ByteSender} used for sending messages.
   *
   * ByteSender may be shared between multiple instances of kademlia.
   *
   * @param byteSender ByteSender used for sending message
   * @return this
   */
  public KademliaRoutingBuilder setByteSender(ByteSender byteSender) {
    mMessageSender = new MessageSenderAdapter(byteSender);
    return this;
  }

  /**
   * Set {@link me.gregorias.ghoul.network.ByteListeningService}.
   *
   * ByteListeningService may be shared between multiple instances of kademlia,
   * but they must created from the same builder.
   *
   * @param byteListeningService ByteListeningService used for listening for messages
   * @return this
   */
  public KademliaRoutingBuilder setByteListeningService(ByteListeningService byteListeningService) {
    mListeningAdapter = new MessageListeningServiceAdapter(byteListeningService);
    return this;
  }

  public KademliaRoutingBuilder setCertificateStorage(CertificateStorage storage) {
    mCertificateStorage = storage;
    return this;
  }

  /**
   * Set concurrency parameter
   *
   * @param alpha concurrency parameter used for sending
   * @return this
   */
  public KademliaRoutingBuilder setConcurrencyParameter(int alpha) {
    mAlpha = alpha;
    return this;
  }

  public KademliaRoutingBuilder setExecutor(ScheduledExecutorService executor) {
    mExecutor = executor;
    return this;
  }

  /**
   * Set how often should kademlia peer send a heart beat signal refreshing it's entry in other
   * nodes.
   *
   * @param heartBeatDelay heart beat delay
   * @param heartBeatDelayUnit heart beat delay unit
   */
  public void setHeartBeatDelay(long heartBeatDelay, TimeUnit heartBeatDelayUnit) {
    mHeartBeatDelay = heartBeatDelay;
    mHeartBeatDelayUnit = heartBeatDelayUnit;
  }

  public KademliaRoutingBuilder setNetworkAddressDiscovery(
      NetworkAddressDiscovery networkAddressDiscovery) {
    mNetworkAddressDiscovery = networkAddressDiscovery;
    return this;
  }

  /**
   * Set initial peers connected to the network with known Kademlia keys.
   *
   * @param peerInfos collection of peers
   * @return this
   */
  public KademliaRoutingBuilder setInitialPeersWithKeys(Collection<NodeInfo> peerInfos) {
    assert peerInfos != null;
    mInitialPeersWithKeys = new ArrayList<>(peerInfos);
    return this;
  }

  public KademliaRoutingBuilder setKey(Key key) {
    mKey = key;
    return this;
  }

  public KademliaRoutingBuilder setMessageTimeout(long timeout, TimeUnit unit) {
    mMessageTimeout = timeout;
    mMessageTimeoutUnit = unit;
    return this;
  }

  public KademliaRoutingBuilder setPersonalCertificateManager(PersonalCertificateManager mgr) {
    mPersonalCertificateManager = mgr;
    return this;
  }


  public KademliaRoutingBuilder setPersonalKeyPair(KeyPair keyPair) {
    mPersonalKeyPair = keyPair;
    return this;
  }

  /**
   * A wrapper on top of {@link me.gregorias.ghoul.kademlia.ListeningService} which may
   * have multiple kademlia peers as its listeners.
   */
  private static class DemultiplexingMessageListeningService implements ListeningService {
    private final ListeningService mBaseListeningService;
    private final Key mKey;

    public DemultiplexingMessageListeningService(Key key, ListeningService baseListeningService) {
      mKey = key;
      mBaseListeningService = baseListeningService;
    }

    @Override
    public void registerListener(MessageMatcher matcher, MessageListener listener) {
      LOGGER.debug("DemultiplexingMessageListeningService.registerListener({}, {})",
          matcher,
          listener);
      mBaseListeningService.registerListener((KademliaMessage msg) ->
          matcher.match(msg) && (msg instanceof GetDHTKeyMessage
              || msg.getDestinationNodeInfo().getKey().equals(mKey)),
          listener);
    }

    @Override
    public void unregisterListener(MessageListener listener) {
      LOGGER.debug("DemultiplexingMessageListeningService.unregisterListener({}, {})");
      mBaseListeningService.unregisterListener(listener);
    }
  }

  private void checkIfByteListeningServiceIsSet() {
    if (mListeningAdapter == null) {
      throw new IllegalStateException("Listening service is not set.");
    }
  }

  private void checkIfByteSenderIsSet() {
    if (mMessageSender == null) {
      throw new IllegalStateException("Byte sender is not set.");
    }
  }

  private void checkIfCertificateStorageIsSet() {
    if (mCertificateStorage == null) {
      throw new IllegalStateException("Certificate storage is not set.");
    }
  }

  private void checkIfExecutorIsSet() {
    if (mExecutor == null) {
      throw new IllegalStateException("Executor is not set.");
    }
  }

  private void checkIfNetworkAddressDiscoveryIsSet() {
    if (mNetworkAddressDiscovery == null) {
      throw new IllegalStateException("Network address discovery is not set.");
    }
  }

  private void checkIfPersonalCertificateManagerIsSet() {
    if (mPersonalCertificateManager == null) {
      throw new IllegalStateException("Personal certificate manager is not set.");
    }
  }

  private void checkIfKeyPairIsSet() {
    if (mPersonalKeyPair == null) {
      throw new IllegalStateException("Personal private key is not set.");
    }
  }

  private Key getSetKeyOrCreateNew() {
    if (mKey != null) {
      return mKey;
    } else {
      return Key.newRandomKey(mRandom);
    }
  }
}

