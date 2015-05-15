package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.GetKeyMessage;
import me.gregorias.ghoul.kademlia.data.GetKeyReplyMessage;
import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.kademlia.data.NodeInfo;
import me.gregorias.ghoul.kademlia.data.PutKeyMessage;
import me.gregorias.ghoul.network.NetworkAddressDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Class which handles the storage part of the Kademlia protocol.
 */
public class KademliaStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaStore.class);
  private static final KademliaStoreMessageMatcher STORE_MATCHER =
      new KademliaStoreMessageMatcher();
  private final NetworkAddressDiscovery mNetworkAddressDiscovery;
  private final KademliaRouting mKademliaRouting;
  private final MessageSender mSender;
  private final ListeningService mListeningService;
  private final MessageListener mMessageListener;
  private final Store mStore;
  private final Map<Integer, Collection<BlockingQueue<GetKeyTaskEvent>>> mGetKeyListeners;
  private final Random mRandom;

  public KademliaStore(NetworkAddressDiscovery networkAddressDiscovery,
                       KademliaRouting kademliaRouting,
                       MessageSender sender,
                       ListeningService listeningService,
                       Store store,
                       Random random) {
    mNetworkAddressDiscovery = networkAddressDiscovery;
    mKademliaRouting = kademliaRouting;
    mSender = sender;
    mStore = store;
    mListeningService = listeningService;
    mMessageListener = new MessageListenerImpl();
    mGetKeyListeners = new HashMap<>();
    mRandom = random;
  }

  public synchronized  Collection<byte[]> getKey(Key key) throws IOException, KademliaException {
    LOGGER.debug("getKey({})", key);
    Collection<byte[]> foundKeys = new ArrayList<>();
    if (mStore.contains(key)) {
      foundKeys.add(mStore.get(key).get());
    }
    GetKeyTask task = new GetKeyTask(key, 1);
    foundKeys.addAll(task.call());
    LOGGER.debug("getKey({}) -> number of found keys : {}", key, foundKeys.size());
    return foundKeys;
  }

  public synchronized  void putKey(Key key, byte[] value)
      throws IOException, KademliaException, InterruptedException {
    Collection<NodeInfo> targetNodes = mKademliaRouting.findClosestNodes(key);
    putKeysOnTargetNodes(key, value, targetNodes);
  }

  public synchronized void startUp() {
    mListeningService.registerListener(STORE_MATCHER, mMessageListener);
  }

  public synchronized void shutDown() {
    mListeningService.unregisterListener(mMessageListener);
  }

  private class GetKeyTask implements Callable<Collection<byte[]>> {
    private final Key mSearchedKey;
    private final int mConcurrency;
    private final BlockingQueue<GetKeyTaskEvent> mEventQueue;
    private final Collection<byte[]> mFoundData;
    private int mRespondedCount;

    /**
     * Map from node's key to message id assigned to the reply of that node.
     */
    private final Map<Key, Integer> mRegisteredListeners;

    public GetKeyTask(Key key, int concurrency) {
      mSearchedKey = key;
      mConcurrency = concurrency;
      mEventQueue = new LinkedBlockingQueue<>();
      mRegisteredListeners = new HashMap<>();
      mFoundData = new ArrayList<>();
      mRespondedCount = 0;
    }

    @Override
    public Collection<byte[]> call() {
      LOGGER.debug("GetKeyTask.call(): key = {}", mSearchedKey);
      int queriedCount = 0;

      try {
        List<NodeInfo> targetNodes = new ArrayList<>(
            mKademliaRouting.findClosestNodes(mSearchedKey));
        queriedCount += sendInitialRequests(targetNodes);
        while (mRespondedCount < targetNodes.size()) {
          GetKeyTaskEvent event = mEventQueue.take();
          if (event instanceof GetKeyTaskReplyEvent) {
            handleReply(((GetKeyTaskReplyEvent) event).mMsg);
          } else if (event instanceof GetKeyTaskTimeoutEvent) {
            handleTimeOut(((GetKeyTaskTimeoutEvent) event).mTimedOutNodeInfo);
          } else {
            throw new IllegalStateException("Unexpected event has been received.");
          }
          queriedCount += sendAdditionalRequests(targetNodes, queriedCount, mRespondedCount);
          LOGGER.trace("GetKeyTask.call(): handled {} nodes of {}", mRespondedCount,
              targetNodes.size());
        }
      } catch (InterruptedException e) {
        LOGGER.error("Received unexpected interrupt.", e);
      } catch (KademliaException e) {
        LOGGER.error("Received kademlia exception.", e);
      }

      mEventQueue.clear();
      LOGGER.debug("GetKeyTask.call() -> returning found data.");
      return mFoundData;
    }

    private void handleReply(GetKeyReplyMessage msg) {
      if (msg.getData().isPresent()) {
        mFoundData.add(msg.getData().get());
      }

      unregisterFindNodeTaskEventListener(msg.getId(), mEventQueue);
      if (mRegisteredListeners.containsKey(msg.getSourceNodeInfo().getKey())) {
        mRegisteredListeners.remove(msg.getSourceNodeInfo().getKey());
        mRespondedCount += 1;
      }
    }

    private void handleTimeOut(NodeInfo timedOutNodeInfo) {
      LOGGER.trace("GetKeyTask.handleTimeOut({})", timedOutNodeInfo);
      if (mRegisteredListeners.containsKey(timedOutNodeInfo.getKey())) {
        unregisterFindNodeTaskEventListener(mRegisteredListeners.get(timedOutNodeInfo.getKey()),
            mEventQueue);
        mRegisteredListeners.remove(timedOutNodeInfo.getKey());
        mRespondedCount += 1;
      }
    }

    private int sendAdditionalRequests(List<NodeInfo> targetNodes,
                                       int queriedCount,
                                       int respondedCount) {
      int requestCount = Math.min(mConcurrency - (respondedCount - queriedCount),
          targetNodes.size() - queriedCount);
      for (int i = 0; i < requestCount; ++i) {
        NodeInfo target = targetNodes.get(i + queriedCount);
        sendGetKeyMessage(target);
      }
      return requestCount;
    }

    private int sendInitialRequests(List<NodeInfo> targetNodes) {
      return sendAdditionalRequests(targetNodes, 0, 0);
    }

    private void sendGetKeyMessage(NodeInfo destination) {
      LOGGER.trace("GetKeyTask.sendGetKeyMessage({})", destination);
      int messageId = registerGetKeyTaskEventListener(mEventQueue);
      mRegisteredListeners.put(destination.getKey(), messageId);
      mSender.sendMessage(destination.getSocketAddress(),
          new GetKeyMessage(getLocalNodeInfo(), destination, messageId, mSearchedKey));
    }
  }

  private interface GetKeyTaskEvent {
  }

  private static class GetKeyTaskReplyEvent implements GetKeyTaskEvent {
    public final GetKeyReplyMessage mMsg;

    public GetKeyTaskReplyEvent(GetKeyReplyMessage msg) {
      mMsg = msg;
    }
  }

  private static class GetKeyTaskTimeoutEvent implements GetKeyTaskEvent {
    public final NodeInfo mTimedOutNodeInfo;

    public GetKeyTaskTimeoutEvent(NodeInfo timedOutNodeInfo) {
      mTimedOutNodeInfo = timedOutNodeInfo;
    }
  }

  /**
   * Handler for incoming kademlia store messages.
   */
  private class MessageListenerImpl implements MessageListener {
    @Override
    public void receive(KademliaMessage msg) {
      LOGGER.trace("MessageListenerImpl.receive({})", msg);
      if (msg instanceof GetKeyMessage) {
        handleGetKeyMessage((GetKeyMessage) msg);
      } else if (msg instanceof GetKeyReplyMessage) {
        handleGetKeyReplyMessage((GetKeyReplyMessage) msg);
      } else if (msg instanceof PutKeyMessage) {
        handlePutKeyMessage((PutKeyMessage) msg);
      } else {
        LOGGER.warn("Received unknown message: {}", msg);
      }
    }
  }

  private static class KademliaStoreMessageMatcher implements MessageMatcher {
    @Override
    public boolean match(KademliaMessage msg) {
      return msg instanceof GetKeyMessage
          || msg instanceof GetKeyReplyMessage
          || msg instanceof PutKeyMessage;
    }
  }

  private void handleGetKeyMessage(GetKeyMessage msg) {
    LOGGER.debug("handleGetKeyMessage({})", msg);
    Optional<byte[]> optionalData = mStore.get(msg.getKey());
    GetKeyReplyMessage reply = new GetKeyReplyMessage(msg.getDestinationNodeInfo(),
          msg.getSourceNodeInfo(),
          msg.getId(),
          msg.getKey(),
          optionalData);
    LOGGER.debug("handleGetKeyMessage({}) -> reply : {}", msg, reply);
    mSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(), reply);
  }

  /**
   * Notify pertinent find node listeners about find node reply message.
   *
   * @param msg received reply message
   */
  private void handleGetKeyReplyMessage(GetKeyReplyMessage msg) {
    LOGGER.trace("handleGetKeyReplyMessage({})", msg);
    synchronized (mGetKeyListeners) {
      Collection<BlockingQueue<GetKeyTaskEvent>> listeners = mGetKeyListeners.get(msg.getId());
      try {
        if (listeners != null) {
          LOGGER.trace("handleGetKeyReplyMessage({}): Found listeners for the reply", msg);
          GetKeyTaskEvent event = new GetKeyTaskReplyEvent(msg);
          for (BlockingQueue<GetKeyTaskEvent> queue : listeners) {
            queue.put(event);
          }
        }
      } catch (InterruptedException e) {
        LOGGER.error("handleGetKeyReplyMessage(): unexpected InterruptedException.", e);
        Thread.currentThread().interrupt();
      }
    }
    LOGGER.trace("handleGetKeyReplyMessage({}) -> void", msg);
  }

  private void handlePutKeyMessage(PutKeyMessage msg) {
    mStore.put(msg.getKey(), msg.getData());
  }

  public NodeInfo getLocalNodeInfo() {
    Key key = mKademliaRouting.getLocalKey();
    InetSocketAddress address = mNetworkAddressDiscovery.getNetworkAddress();
    return new NodeInfo(key, address);
  }

  private void putKeysOnTargetNodes(Key key, byte[] value, Collection<NodeInfo> targetNodes) {
    for (NodeInfo target : targetNodes) {
      mSender.sendMessage(target.getSocketAddress(),
          new PutKeyMessage(getLocalNodeInfo(), target, 0, key, value));
    }
  }

  /**
   * Register input queue and assigns it random non-conflicting id.
   *
   * @param queue Queue which listens for event.
   * @return random id under which given queue has been put.
   */
  private int registerGetKeyTaskEventListener(BlockingQueue<GetKeyTaskEvent> queue) {
    synchronized (mGetKeyListeners) {
      int id = mRandom.nextInt();
      if (mGetKeyListeners.containsKey(id)) {
        mGetKeyListeners.get(id).add(queue);
      } else {
        Collection<BlockingQueue<GetKeyTaskEvent>> queueList = new ArrayList<>();
        queueList.add(queue);
        mGetKeyListeners.put(id, queueList);
      }
      return id;
    }
  }

  /**
   * Unregister input queue under this id if it exists.
   *
   * @param id id to unregister
   * @param queue queue to unregister
   */
  private void unregisterFindNodeTaskEventListener(int id,
                                                   BlockingQueue<GetKeyTaskEvent> queue) {
    synchronized (mGetKeyListeners) {
      Collection<BlockingQueue<GetKeyTaskEvent>> queueCollection = mGetKeyListeners.get(id);
      if (queueCollection != null) {
        queueCollection.remove(queue);
        if (queueCollection.isEmpty()) {
          mGetKeyListeners.remove(id);
        }
      }
    }
  }
}
