package me.gregorias.ghoul.kademlia;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import me.gregorias.ghoul.kademlia.data.FindNodeMessage;
import me.gregorias.ghoul.kademlia.data.FindNodeReplyMessage;
import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.kademlia.data.NodeInfo;
import me.gregorias.ghoul.kademlia.data.PingMessage;
import me.gregorias.ghoul.kademlia.data.PongMessage;
import me.gregorias.ghoul.network.ByteListeningService;
import me.gregorias.ghoul.network.ByteSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder of Kademlia routing peers.
 *
 * If you want multiple kademlia peers on the same listening connection you have
 * to:
 * 1. Set {@link me.gregorias.ghoul.kademlia.ListeningService} only once.
 * 2. Use the same builder for all peers.
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

  private long mMessageTimeout = 5000;
  private TimeUnit mMessageTimeoutUnit = TimeUnit.MILLISECONDS;

  private long mHeartBeatDelay = 2;
  private TimeUnit mHeartBeatDelayUnit = TimeUnit.MINUTES;

  private ListeningService mListeningAdapter;
  private DemultiplexingMessageListener mDemultiplexingListener;

  private MessageSenderAdapter mMessageSender;
  private ScheduledExecutorService mExecutor;
  private int mBucketSize = DEFAULT_BUCKET_SIZE;
  private int mAlpha = DEFAULT_ALPHA;
  private Key mKey;
  private InetSocketAddress mLocalAddress;
  private Collection<NodeInfo> mInitialPeersWithKeys = new LinkedList<>();

  public KademliaRoutingBuilder(Random random) {
    mRandom = random;
  }

  /**
   * Create inactive Kademlia peer.
   *
   * @return Kademlia peer with parameters set before.
   */
  public KademliaRouting createPeer() {
    LOGGER.info("createPeer()");

    checkIfByteListeningServiceIsSet();
    checkIfByteSenderIsSet();
    checkIfExecutorIsSet();
    checkIfLocalAddressIsSet();

    Key usedKey = getSetKeyOrCreateNew();
    NodeInfo localNodeInfo = new NodeInfo(usedKey, mLocalAddress);

    ListeningService listeningService = new MessageListeningServiceImpl(usedKey,
        mDemultiplexingListener);

    LOGGER.debug("createPeer() -> Key: {}", usedKey);
    return new KademliaRoutingImpl(
        localNodeInfo,
        mMessageSender,
        listeningService,
        mBucketSize,
        mAlpha,
        mInitialPeersWithKeys,
        mMessageTimeout,
        mMessageTimeoutUnit,
        mHeartBeatDelay,
        mHeartBeatDelayUnit,
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
    mDemultiplexingListener = new DemultiplexingMessageListener(mListeningAdapter);
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

  public KademliaRoutingBuilder setLocalAddress(InetSocketAddress localAddress) {
    mLocalAddress = localAddress;
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

  /**
   * A wrapper on top of {@link me.gregorias.ghoul.kademlia.ListeningService} which may
   * have multiple kademlia peers as its listeners.
   */
  private static class DemultiplexingMessageListener implements MessageListener {

    private final ListeningService mBaseListeningService;
    private final Map<Key, MessageListener> mListenerMap;
    private final ReadWriteLock mRWLock;
    private final Lock mReadLock;
    private final Lock mWriteLock;

    public DemultiplexingMessageListener(ListeningService baseListeningService) {
      mBaseListeningService = baseListeningService;
      mListenerMap = new HashMap<>();
      mRWLock = new ReentrantReadWriteLock();
      mReadLock = mRWLock.readLock();
      mWriteLock = mRWLock.writeLock();
    }

    public void registerListener(Key key, MessageListener listener) {
      LOGGER.debug("DemultiplexingMessageListener.registerListener({}, {})", key, listener);
      mWriteLock.lock();
      try {
        if (mListenerMap.isEmpty()) {
          mBaseListeningService.registerListener(this);
        }
        if (mListenerMap.containsKey(key)) {
          throw new IllegalStateException(String.format("Kademlia peer at key: %s"
              + " has already registered its listener.", key));
        }
        mListenerMap.put(key, listener);
      } finally {
        mWriteLock.unlock();
      }
    }

    public void unregisterListener(Key key) {
      LOGGER.debug("DemultiplexingMessageListener.unregisterListener({}, {})");
      mWriteLock.lock();
      try {
        if (!mListenerMap.containsKey(key)) {
          throw new IllegalStateException(String.format("Kademlia peer at key: %s"
              + " has no registered listener.", key));
        }
        mListenerMap.remove(key);
        if (mListenerMap.isEmpty()) {
          mBaseListeningService.unregisterListener(this);
        }
      } finally {
        mWriteLock.unlock();
      }
    }

    @Override
    public void receiveFindNodeMessage(FindNodeMessage msg) {
      forwardMessageToAppropriateListener(msg);
    }

    @Override
    public void receiveFindNodeReplyMessage(FindNodeReplyMessage msg) {
      forwardMessageToAppropriateListener(msg);
    }

    @Override
    public void receivePingMessage(PingMessage msg) {
      forwardMessageToAppropriateListener(msg);
    }

    @Override
    public void receivePongMessage(PongMessage msg) {
      forwardMessageToAppropriateListener(msg);
    }

    private void forwardMessageToAppropriateListener(KademliaMessage msg) {
      mReadLock.lock();
      try {
        Optional<MessageListener> listener = getRecipient(msg);
        if (listener.isPresent()) {
          listener.get().receive(msg);
        }
      } finally {
        mReadLock.unlock();
      }
    }

    private Optional<MessageListener> getRecipient(KademliaMessage msg) {
      Key destKey = msg.getDestinationNodeInfo().getKey();
      MessageListener listener = mListenerMap.get(destKey);
      if (listener == null) {
        LOGGER.debug("getRecipient({}) -> Received message to unknown kademlia peer.", msg);
        return Optional.empty();
      }
      return Optional.of(listener);
    }

  }

  /**
   * ListeningService which uses acts as a proxy to {@link DemultiplexingMessageListener} for
   * specific key.
   */
  private static class MessageListeningServiceImpl implements ListeningService {
    private final DemultiplexingMessageListener mDemux;
    private final Key mKey;

    public MessageListeningServiceImpl(Key key, DemultiplexingMessageListener demux) {
      mDemux = demux;
      mKey = key;
    }

    @Override
    public void registerListener(MessageListener listener) {
      LOGGER.trace("MessageListeningServiceImpl.registerListener({})", listener);
      mDemux.registerListener(mKey, listener);
    }

    @Override
    public void unregisterListener(MessageListener listener) {
      LOGGER.trace("MessageListeningServiceImpl.unregisterListener({})", listener);
      mDemux.unregisterListener(mKey);
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

  private void checkIfExecutorIsSet() {
    if (mExecutor == null) {
      throw new IllegalStateException("Executor is not set.");
    }
  }

  private void checkIfLocalAddressIsSet() {
    if (mLocalAddress == null) {
      throw new IllegalStateException("Local address is not set.");
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

