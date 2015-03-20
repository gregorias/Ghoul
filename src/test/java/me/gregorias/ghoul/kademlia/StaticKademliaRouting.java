package me.gregorias.ghoul.kademlia;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link me.gregorias.ghoul.kademlia.KademliaRouting} implementation which has static routing
 * table.
 *
 * @author Grzegorz Milka
 */
final class StaticKademliaRouting implements KademliaRouting {
  private static final Logger LOGGER = LoggerFactory.getLogger(StaticKademliaRouting.class);

  private final Key mLocalKey;
  private InetSocketAddress mLocalAddress;
  private final MessageSender mMessageSender;
  private final ListeningService mListeningService;
  private final MessageListener mMessageListener;
  private final KademliaRoutingTable mRoutingTable;

  private final Lock mReadRunningLock;
  private final Lock mWriteRunningLock;

  private boolean mIsRunning = false;

  private MessageListener mAuxListener;

  public StaticKademliaRouting(NodeInfo localNodeInfo,
                            MessageSender sender,
                            ListeningService listeningService,
                            KademliaRoutingTable routingTable) {
    mLocalKey = localNodeInfo.getKey();
    mLocalAddress = localNodeInfo.getSocketAddress();
    mMessageSender = sender;
    mListeningService = listeningService;
    mMessageListener = new MessageListenerImpl();
    mRoutingTable = routingTable;

    ReadWriteLock rwLock = new ReentrantReadWriteLock();
    mReadRunningLock = rwLock.readLock();
    mWriteRunningLock = rwLock.writeLock();
  }

  @Override
  public Collection<NodeInfo> findClosestNodes(Key key) {
    return findClosestNodes(key, 0);
  }

  @Override
  public Collection<NodeInfo> findClosestNodes(Key key, int size) {
    throw new UnsupportedOperationException("findClosestNodes is unsupported for this"
        + " fake implementation");
  }

  @Override
  public Collection<NodeInfo> getFlatRoutingTable() {
    return mRoutingTable.flatten();
  }

  @Override
  public Key getLocalKey() {
    return mLocalKey;
  }

  public boolean isRunning() {
    mReadRunningLock.lock();
    boolean isRunning = mIsRunning;
    mReadRunningLock.unlock();
    return isRunning;
  }

  @Override
  public void registerNeighbourListener(NeighbourListener listener) {
  }

  @Override
  public void unregisterNeighbourListener() {
  }

  public void sendPingToNode(Key key) {
    List<Key> keys = new LinkedList<>();
    keys.add(key);
    sendPingToNodes(keys);
  }

  public void sendPingToNodes(Collection<Key> keys) {
    Collection<NodeInfo> nodeInfos = new LinkedList<>();
    for (Key key : keys) {
      Optional<NodeInfo> nodeInfo = mRoutingTable.get(key);
      if (nodeInfo.isPresent()) {
        nodeInfos.add(nodeInfo.get());
      } else {
        throw new IllegalArgumentException(String.format("Key: %s is not present in"
            + " routing table.", key));
      }
    }
    sendPingsToNodes(nodeInfos);
  }

  public synchronized void setMessageListenerAdditionalActions(MessageListener listener) {
    mAuxListener = listener;
  }

  @Override
  public void start() throws KademliaException {
    mWriteRunningLock.lock();
    try {
      LOGGER.info("start()");
      if (mIsRunning) {
        throw new IllegalStateException("Kademlia has already started.");
      }

      LOGGER.trace("startUp() -> registerListener");
      mListeningService.registerListener(mMessageListener);
      mIsRunning = true;
    } finally {
      mWriteRunningLock.unlock();
    }
  }

  @Override
  public void stop() throws KademliaException {
    mWriteRunningLock.lock();
    try {
      LOGGER.info("stop()");
      if (!mIsRunning) {
        throw new IllegalStateException("Kademlia is not running.");
      }
      mListeningService.unregisterListener(mMessageListener);
      mIsRunning = false;
    } finally {
      mWriteRunningLock.unlock();
    }
    LOGGER.info("stop(): void");
  }

  private class MessageListenerImpl implements MessageListener {

    @Override
    public void receiveFindNodeMessage(FindNodeMessage msg) {
      notifyAuxListener(msg);
      mMessageSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(),
          new FindNodeReplyMessage(getLocalNodeInfo(),
              msg.getSourceNodeInfo(),
              msg.getId(),
              mRoutingTable.getClosestNodes(msg.getSearchedKey())));
    }

    @Override
    public void receiveFindNodeReplyMessage(FindNodeReplyMessage msg) {
      notifyAuxListener(msg);
    }

    @Override
    public void receivePingMessage(PingMessage msg) {
      notifyAuxListener(msg);
      mMessageSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(),
          new PongMessage(getLocalNodeInfo(),
              msg.getSourceNodeInfo(),
              msg.getId()));
    }

    @Override
    public void receivePongMessage(PongMessage msg) {
      notifyAuxListener(msg);
    }

    private void notifyAuxListener(KademliaMessage msg) {
      synchronized (StaticKademliaRouting.this) {
        if (mAuxListener != null) {
          mAuxListener.receive(msg);
        }
      }
    }
  }

  private NodeInfo getLocalNodeInfo() {
    return new NodeInfo(mLocalKey, mLocalAddress);
  }

  private void sendPingsToNodes(Collection<NodeInfo> routingTableNodes) {
    for (NodeInfo info : routingTableNodes) {
      mMessageSender.sendMessage(info.getSocketAddress(),
          new PingMessage(getLocalNodeInfo(), info, 1));
    }
  }
}

