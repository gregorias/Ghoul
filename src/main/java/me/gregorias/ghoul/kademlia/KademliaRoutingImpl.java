package me.gregorias.ghoul.kademlia;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import jdk.nashorn.internal.ir.Block;
import me.gregorias.ghoul.network.NetworkAddressDiscovery;
import me.gregorias.ghoul.utils.BoundedSortedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;

import static java.lang.Math.min;

/** Implementation of Kademlia routing. */
class KademliaRoutingImpl implements KademliaRouting {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaRoutingImpl.class);
  private final Random mRandom;

  private final Collection<InetSocketAddress> mInitialPeerAddresses;
  private final Collection<NodeInfo> mInitialKnownPeers;

  private final List<List<NodeInfo>> mBuckets;

  /**
   * List of nodes expecting to be put into a bucket if a place for them is
   * emptied.
   */
  private final List<List<NodeInfo>> mBucketsWaitingList;
  private final Key mLocalKey;
  private InetSocketAddress mLocalAddress;
  private final MessageSender mMessageSender;
  private final ListeningService mListeningService;
  private final MessageListener mMessageListener;
  private final NetworkAddressDiscovery mNetAddrDiscovery;
  private final int mBucketSize;

  /**
   * Map from expected message id to a collection of FindNodeTaskEvent queues expecting response
   * on that id.
   */
  private final Map<Integer, Collection<BlockingQueue<FindNodeTaskEvent>>> mFindNodeListeners;

  /**
   * Concurrency coefficient in node lookups
   */
  private final int mAlpha;
  private final int mEntryRefreshDelay;

  private final long mMessageTimeout;
  private final TimeUnit mMessageTimeoutUnit;

  private final ScheduledExecutorService mScheduledExecutor;

  private final EntryRefresher mEntryRefresher;
  private final AddressChangeObserver mAddrChangeObserver;

  private final Lock mReadRunningLock;
  private final Lock mWriteRunningLock;

  private boolean mIsRunning = false;
  private ScheduledFuture<?> mRefreshFuture = null;


  /** TODO */
  KademliaRoutingImpl(NodeInfo localNodeInfo,
                      MessageSender sender,
                      ListeningService listeningService,
                      NetworkAddressDiscovery netAddrDiscovery,
                      ScheduledExecutorService scheduledExecutor,
                      int bucketSize,
                      int alpha,
                      int entryRefreshDelay,
                      Collection<InetSocketAddress> initialPeerAddresses,
                      Collection<NodeInfo> initialKnownPeers,
                      long messageTimeout,
                      TimeUnit messageTimeoutUnit,
                      Random random) {
    assert bucketSize > 0;
    mRandom = random;
    mBucketSize = bucketSize;
    mAlpha = alpha;
    mEntryRefreshDelay = entryRefreshDelay;
    mBuckets = initializeBuckets();
    mBucketsWaitingList = initializeBuckets();
    mLocalKey = localNodeInfo.getKey();
    mLocalAddress = localNodeInfo.getSocketAddress();
    mMessageSender = sender;
    mListeningService = listeningService;
    mMessageListener = new MessageListenerImpl();
    mNetAddrDiscovery = netAddrDiscovery;

    mFindNodeListeners = new HashMap<>();

    mMessageTimeout = messageTimeout;
    mMessageTimeoutUnit = messageTimeoutUnit;

    mScheduledExecutor = scheduledExecutor;

    mInitialPeerAddresses = new LinkedList<>(initialPeerAddresses);
    mInitialKnownPeers = new LinkedList<>(initialKnownPeers);

    mEntryRefresher = new EntryRefresher();
    mAddrChangeObserver = new AddressChangeObserver();

    ReadWriteLock rwLock = new ReentrantReadWriteLock();
    mReadRunningLock = rwLock.readLock();
    mWriteRunningLock = rwLock.writeLock();
  }

  @Override
  public Collection<NodeInfo> findClosestNodes(Key key) throws InterruptedException {
    return findClosestNodes(key, mBucketSize);
  }

  @Override
  public Collection<NodeInfo> findClosestNodes(Key key, int expectedFoundNodesSize) throws
      InterruptedException {
    FindNodeTask findNodeTask = new FindNodeTask(key, mAlpha, expectedFoundNodesSize);
    return findNodeTask.call();
  }

  @Override
  public Key getLocalKey() {
    return mLocalKey;
  }

  @Override
  /** TODO */
  public Collection<NodeInfo> getRoutingTable() {
    Collection<NodeInfo> routingTable = new ArrayList<>();
    for (Collection<NodeInfo> bucket : mBuckets) {
      routingTable.addAll(bucket);
    }
    return routingTable;
  }

  /** TODO */
  public boolean isRunning() {
    mReadRunningLock.lock();
    boolean isRunning = mIsRunning;
    mReadRunningLock.unlock();
    return isRunning;
  }

  @Override
  /** TODO */
  public void start() throws KademliaException {
    LOGGER.debug("start()");
    mWriteRunningLock.lock();
    try {
      if (mIsRunning) {
        throw new IllegalStateException("Kademlia has already started.");
      }

      /* Connect to initial peers */
      clearBuckets();
      LOGGER.trace("start() -> addPeersToBuckets");
      addPeersToBuckets(mInitialKnownPeers);

      mNetAddrDiscovery.addObserver(mAddrChangeObserver);

      LOGGER.trace("start() -> registerListener");
      mListeningService.registerListener(mMessageListener);
      mRefreshFuture = mScheduledExecutor.schedule(mEntryRefresher, 0, TimeUnit.MILLISECONDS);
      mIsRunning = true;
    } finally {
      mWriteRunningLock.unlock();
    }
  }

  @Override
  /** TODO */
  public void stop() throws KademliaException {
    mWriteRunningLock.lock();
    try {
      LOGGER.info("stop()");
      if (!mIsRunning) {
        throw new IllegalStateException("Kademlia is not running.");
      }
      mRefreshFuture.cancel(true);
      mListeningService.unregisterListener(mMessageListener);
      mIsRunning = false;
    } finally {
      mWriteRunningLock.unlock();
    }
    LOGGER.info("stop() -> void");
  }

  /** TODO */
  private class AddressChangeObserver implements Observer {
    @Override
    public void update(Observable arg0, Object arg1) {
      InetSocketAddress newAddress = (InetSocketAddress) arg1;
      changeAddress(newAddress);
      mScheduledExecutor.schedule(mEntryRefresher, 0, TimeUnit.MILLISECONDS);
    }
  }

  /** TODO */
  private class EntryRefresher implements Runnable {
    private final Logger mLogger = LoggerFactory.getLogger(EntryRefresher.class);

    @Override
    public synchronized void run() {
      mLogger.debug("run()");
      mReadRunningLock.lock();
      try {
        if (!mIsRunning) {
          return;
        }
        findClosestNodes(mLocalKey);
        mRefreshFuture = mScheduledExecutor.schedule(this, mEntryRefreshDelay,
            TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        mLogger.error("Caught unexpected InterruptedException.", e);
      } finally {
        mReadRunningLock.unlock();
      }
      mLogger.debug("run(): void");
    }

  }

  /* Find node search implementation */

  /**
   * This is a runnable task which performs Kademlia's iterative algorithm for finding a node.
   *
   * This task uses an actor model of operation, it sends up to alpha queries at one time and waits
   * for replies or timeouts. It returns result once all top k candidates have replied or there are
   * no more candidates.
   *
   * This actor uses BlockingQueue for communication and registers message listener in
   * mFindNodeListeners.
   *
   * NOTE If this search gets a conflicting IP address to a candidate key then it this new IP
   * address is ignored. IP addresses should be updated through direct messages, not a find node
   * search.
   */
  private class FindNodeTask implements Callable<Collection<NodeInfo>> {
    private final Key mSearchedKey;
    private final int mAlpha;
    private final int mResponseSize;
    private final BlockingQueue<FindNodeTaskEvent> mEventQueue;

    /**
     * Map from node's key to message id assigned to the reply of that node.
     */
    private final Map<Key, Integer> mRegisteredListeners;

    // This variable is a member, because all methods rely on it.
    private SortedMap<Key, NodeInfoWithStatus> mAllNodes;

    public FindNodeTask(Key key, int alpha, int responseSize) {
      mSearchedKey = key;
      mAlpha = alpha;
      mResponseSize = responseSize;
      mEventQueue = new LinkedBlockingQueue<>();
      mRegisteredListeners = new HashMap<>();
    }

    @Override
    public Collection<NodeInfo> call() {
      LOGGER.debug("FindNodeTask.run(): key = {}", mSearchedKey);
      mReadRunningLock.lock();
      try {
        if (!mIsRunning) {
          throw new IllegalStateException("Called findClosestNodes() on nonrunning kademlia.");
        }

        mAllNodes = initializeAllNodes();
        sendRequestsToInitialCandidates();

        // While there are pending nodes wait for replies and timeouts. Send new messages when
        // possible candidates shows up
        while (getPendingNodes().size() > 0) {
          FindNodeTaskEvent event = mEventQueue.take();
          if (event instanceof ReplyFindNodeTaskEvent) {
            handleReply(((ReplyFindNodeTaskEvent) event).mMsg);
          } else if (event instanceof TimeoutFindNodeTaskEvent) {
            handleTimeOut(((TimeoutFindNodeTaskEvent) event).mTimedOutNodeInfo);
          } else {
            throw new IllegalStateException("Unexpected event has been received.");
          }

          sendQueriesToValidCandidates();
        }

        unregisterAllEventListenersForThisTask();
        mEventQueue.clear();
        return  getQueriedNodes().stream().limit(mResponseSize).collect(Collectors.toList());
      } catch (InterruptedException e) {
        throw new IllegalStateException("FindNodeTask.run(): Unexpected interrupt.", e);
      } finally {
        mReadRunningLock.unlock();
      }
    }

    private class NodeInfoWithStatus {
      public final NodeInfo mInfo;
      public FindNodeStatus mStatus;

      public NodeInfoWithStatus(NodeInfo info) {
        mInfo = info;
        mStatus = FindNodeStatus.UNQUERIED;
      }
    }

    private void addNewCandidateNodes(Collection<NodeInfo> foundNodes) {
      foundNodes.stream().
          filter((NodeInfo info) -> !mAllNodes.containsKey(info.getKey())).
          forEach((NodeInfo info) -> mAllNodes.put(info.getKey(), new NodeInfoWithStatus(info)));
    }

    private Collection<NodeInfo> getPendingNodes() {
      return mAllNodes.values().
          stream().
          filter((NodeInfoWithStatus info) -> info.mStatus == FindNodeStatus.PENDING).
          map((NodeInfoWithStatus info) -> info.mInfo).
          collect(Collectors.toList());
    }

    private Collection<NodeInfo> getQueriedNodes() {
      return mAllNodes.values().
          stream().
          filter((NodeInfoWithStatus info) -> info.mStatus == FindNodeStatus.QUERIED).
          map((NodeInfoWithStatus info) -> info.mInfo).
          collect(Collectors.toList());
    }

    /**
     * Picks next valid candidate - unqueried nodes in which are in top mResponseSize active nodes
     * close to searched key.
     *
     * @return Found candidate if any
     */
    private Optional<NodeInfoWithStatus> getValidUnqueriedCandidate() {
      return mAllNodes.values().
          stream().
          filter((NodeInfoWithStatus info) -> info.mStatus != FindNodeStatus.TIMED_OUT).
          limit(mResponseSize).
          filter((NodeInfoWithStatus info) -> info.mStatus == FindNodeStatus.UNQUERIED).
          findFirst();
    }

    private void handleReply(FindNodeReplyMessage replyMessage) {
      NodeInfoWithStatus info = mAllNodes.get(replyMessage.getSourceNodeInfo().getKey());
      if (info != null) {
        unregisterEventListenerForThisTask(info.mInfo.getKey(), replyMessage.getId());
        switch (info.mStatus) {
          case PENDING:
          case TIMED_OUT:
            Collection<NodeInfo> foundNodes = replyMessage.getFoundNodes();
            addNewCandidateNodes(foundNodes);
            info.mStatus = FindNodeStatus.QUERIED;
            break;
          case QUERIED:
            LOGGER.debug("handleReply(): Received a duplicate reply from {}.", info.mInfo.getKey());
            break;
          case UNQUERIED:
            LOGGER.debug("handleReply(): Received a reply from unqueried node: {}.",
                info.mInfo.getKey());
            break;
          default:
            break;
        }
        if (info.mStatus == FindNodeStatus.PENDING) {
          info.mStatus = FindNodeStatus.TIMED_OUT;
        }
      } else {
        LOGGER.warn("handleReply(): Received unexpected reply from unqueried key.");
      }
    }

    private void handleTimeOut(NodeInfo nodeInfo) {
      NodeInfoWithStatus info = mAllNodes.get(nodeInfo.getKey());
      if (info != null) {
        if (info.mStatus == FindNodeStatus.PENDING) {
          info.mStatus = FindNodeStatus.TIMED_OUT;
        }
      } else {
        LOGGER.error("handleTimeOut(): Received unexpected timeout for unknown key.");
      }
    }

    private SortedMap<Key, NodeInfoWithStatus> initializeAllNodes() {
      Collection<NodeInfo> closeNodes = getClosestRoutingTableNodes(mSearchedKey, mResponseSize);
      SortedMap<Key, NodeInfoWithStatus> allNodes = new TreeMap<>(new KeyComparator(mSearchedKey));
      closeNodes.stream().
          forEach((NodeInfo info) -> allNodes.put(info.getKey(), new NodeInfoWithStatus(info)));
      return allNodes;
    }

    private void sendFindNodeMessage(NodeInfoWithStatus nodeInfoWithStatus) {
      assert nodeInfoWithStatus.mStatus == FindNodeStatus.UNQUERIED;

      int messageId = registerFindNodeTaskEventListener(mEventQueue);
      mRegisteredListeners.put(nodeInfoWithStatus.mInfo.getKey(), messageId);

      FindNodeMessage msg = new FindNodeMessage(getLocalNodeInfo(),
          nodeInfoWithStatus.mInfo,
          messageId,
          mSearchedKey);

      mMessageSender.sendMessage(nodeInfoWithStatus.mInfo.getSocketAddress(), msg);
      mScheduledExecutor.schedule(
          () -> {
            boolean hasPutSuccessfully = false;
            while (!hasPutSuccessfully) {
              try {
                mEventQueue.put(new TimeoutFindNodeTaskEvent(nodeInfoWithStatus.mInfo));
                hasPutSuccessfully = true;
              } catch (InterruptedException e) {
                LOGGER.error("TimeoutHandler(): Unexpected interrupt", e);
              }
            }
          },
          mMessageTimeout,
          mMessageTimeoutUnit);
      nodeInfoWithStatus.mStatus = FindNodeStatus.PENDING;
    }

    private void sendQueriesToValidCandidates() {
      boolean doesNotHaveValidCandidates = false;
      while (getPendingNodes().size() < mAlpha && !doesNotHaveValidCandidates) {
        Optional<NodeInfoWithStatus> info = getValidUnqueriedCandidate();
        if (info.isPresent()) {
          sendFindNodeMessage(info.get());
        } else {
          doesNotHaveValidCandidates = true;
        }
      }
    }

    private void sendRequestsToInitialCandidates() {
      Collection<Key> keysToQuery = mAllNodes.keySet().stream().
          limit(min(mResponseSize, mAlpha)).
          collect(Collectors.toList());
      for (Key keyToQuery : keysToQuery) {
        NodeInfoWithStatus status = mAllNodes.get(keyToQuery);
        sendFindNodeMessage(status);
      }
    }

    private void unregisterEventListenerForThisTask(Key key, int id) {
      Integer foundId = mRegisteredListeners.get(key);
      if (foundId == null) {
        LOGGER.warn("unregisterEventListenerForThisTask({}, {}): Received reply from"
            + " unexpected key.", key, id);
      } else if (foundId != id) {
        LOGGER.warn("unregisterEventListenerForThisTask({}, {}): Received reply with"
            + " wrong id.", key, id);
      } else {
        unregisterFindNodeTaskEventListener(id, mEventQueue);
        mRegisteredListeners.remove(key);
      }
    }

    private void unregisterAllEventListenersForThisTask() {
      for (Key key : mRegisteredListeners.keySet()) {
        int listenerId = mRegisteredListeners.get(key);
        unregisterFindNodeTaskEventListener(listenerId, mEventQueue);
      }
      mRegisteredListeners.clear();
    }
  }

  private static enum FindNodeStatus {
    QUERIED,
    TIMED_OUT,
    PENDING,
    UNQUERIED
  }

  private static interface FindNodeTaskEvent { }

  private static class ReplyFindNodeTaskEvent implements FindNodeTaskEvent {
    public final FindNodeReplyMessage mMsg;

    public ReplyFindNodeTaskEvent(FindNodeReplyMessage msg) {
      mMsg = msg;
    }
  }

  private static class TimeoutFindNodeTaskEvent implements FindNodeTaskEvent {
    public final NodeInfo mTimedOutNodeInfo;

    public TimeoutFindNodeTaskEvent(NodeInfo timedOutNodeInfo) {
      mTimedOutNodeInfo = timedOutNodeInfo;
    }
  }

  // TODO This method will go into blocking infinite loop if mFindNodeListeners is full or close to
  // full
  /**
   * Register input queue and assigns it random non-conflicting id.
   *
   * @param queue Queue which listens for event.
   * @return random id under which given queue has been put.
   */
  private int registerFindNodeTaskEventListener(BlockingQueue<FindNodeTaskEvent> queue) {
    synchronized (mFindNodeListeners) {
      int id = mRandom.nextInt();
      if (mFindNodeListeners.containsKey(id)) {
        mFindNodeListeners.get(id).add(queue);
      } else {
        Collection<BlockingQueue<FindNodeTaskEvent>> queueList = new ArrayList<>();
        queueList.add(queue);
        mFindNodeListeners.put(id, queueList);
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
                                                   BlockingQueue<FindNodeTaskEvent> queue) {
    synchronized (mFindNodeListeners) {
      Collection<BlockingQueue<FindNodeTaskEvent>> queueCollection = mFindNodeListeners.get(id);
      if (queueCollection != null) {
        queueCollection.remove(queue);
        if (queueCollection.isEmpty()) {
          mFindNodeListeners.remove(id);
        }
      }
    }
  }

  /* End of find node search implementation */


  /**
   * {@link MessageResponseHandler} which puts responses into given queue.
   *
   * @author Grzegorz Milka
   */
  /** TODO */
  private class QueuedMessageResponseHandler implements MessageResponseHandler {
    private final BlockingQueue<Future<KademliaMessage>> mOutputQueue;

    public QueuedMessageResponseHandler(BlockingQueue<Future<KademliaMessage>> outputQueue) {
      mOutputQueue = outputQueue;
    }

    @Override
    public void onResponse(KademliaMessage response) {
      SettableFuture<KademliaMessage> futureResponse = SettableFuture.<KademliaMessage>create();
      futureResponse.set(response);
      processSenderNodeInfo(response.getSourceNodeInfo());
      mOutputQueue.add(futureResponse);
    }

    @Override
    public void onResponseError(IOException exception) {
      SettableFuture<KademliaMessage> futureResponse = SettableFuture.<KademliaMessage>create();
      futureResponse.setException(exception);
      mOutputQueue.add(futureResponse);
    }

    @Override
    public void onSendSuccessful() {
    }

    @Override
    public void onSendError(IOException exception) {
      SettableFuture<KademliaMessage> futureResponse = SettableFuture.<KademliaMessage>create();
      futureResponse.setException(exception);
      mOutputQueue.add(futureResponse);
    }
  }

  /** TODO */
  private class MessageListenerImpl implements MessageListener {
    @Override
    public FindNodeReplyMessage receiveFindNodeMessage(FindNodeMessage msg) {
      processSenderNodeInfo(msg.getSourceNodeInfo());
      return new FindNodeReplyMessage(getLocalNodeInfo(), msg.getSourceNodeInfo(),
          getClosestRoutingTableNodes(msg.getSearchedKey(), mBucketSize));
    }

    @Override
    public PongMessage receiveGetKeyMessage(GetKeyMessage msg) {
      processSenderNodeInfo(msg.getSourceNodeInfo());
      return new PongMessage(getLocalNodeInfo(), msg.getSourceNodeInfo());
    }

    @Override
    public PongMessage receivePingMessage(PingMessage msg) {
      processSenderNodeInfo(msg.getSourceNodeInfo());
      return new PongMessage(getLocalNodeInfo(), msg.getSourceNodeInfo());
    }
  }

  /** TODO */
  private class PingCheckMessageHandler implements MessageResponseHandler {
    private final int mBucketId;
    private final List<NodeInfo> mBucket;
    private final List<NodeInfo> mBucketWaitingList;

    public PingCheckMessageHandler(int bucketId) {
      mBucketId = bucketId;
      mBucket = mBuckets.get(mBucketId);
      mBucketWaitingList = mBucketsWaitingList.get(mBucketId);
    }

    @Override
    public void onResponse(KademliaMessage response) {
      LOGGER.debug("onResponse()");
      synchronized (mBucket) {
        replaceNodeInfo(mBucket, 0, mBucket.get(0));
        mBucketWaitingList.remove(0);
        checkWaitingQueueAndContinue();
      }
    }

    @Override
    public void onResponseError(IOException exception) {
      LOGGER.debug("onResponseError()", exception);
      onError();
    }

    @Override
    public void onSendSuccessful() {
    }

    @Override
    public void onSendError(IOException exception) {
      LOGGER.debug("onSendError()", exception);
      onError();
    }

    private void checkWaitingQueueAndContinue() {
      if (!mBucketWaitingList.isEmpty()) {
        mMessageSender.sendMessageWithReply(mBucket.get(0).getSocketAddress(), new PingMessage(
            getLocalNodeInfo(), mBucket.get(0)), this);
      }
    }

    private void onError() {
      synchronized (mBucket) {
        replaceNodeInfo(mBucket, 0, mBucketWaitingList.get(0));
        mBucketWaitingList.remove(0);
        checkWaitingQueueAndContinue();
      }
    }
  }

  /** TODO */
  private void addPeersToBuckets(Collection<NodeInfo> initialKnownPeers) {
    List<List<NodeInfo>> tempBuckets = initializeBuckets();
    for (NodeInfo nodeInfo : initialKnownPeers) {
      if (!nodeInfo.getKey().equals(mLocalKey)) {
        tempBuckets.get(getLocalKey().getDistanceBit(nodeInfo.getKey())).add(nodeInfo);
      }
    }
    /* Randomize each bucket and put into real one */
    for (int idx = 0; idx < Key.KEY_LENGTH; ++idx) {
      List<NodeInfo> tempBucket = tempBuckets.get(idx);
      Collections.shuffle(tempBuckets.get(idx), mRandom);
      mBuckets.get(idx).addAll(
          tempBucket.subList(0, min(
              tempBucket.size(), mBucketSize - mBuckets.get(idx).size())));
    }
  }

  /** TODO */
  private void changeAddress(InetSocketAddress newAddress) {
    synchronized (mLocalKey) {
      mLocalAddress = newAddress;
    }
  }

  /** TODO */
  private void checkKeysOfUnknownPeers(Collection<InetSocketAddress> initialPeerAddresses) {
    GetKeyMessage gkMsg = prepareGetKeyMessage();
    BlockingQueue<Future<KademliaMessage>> queue = new LinkedBlockingQueue<>();
    MessageResponseHandler queuedMsgResponseHandler = new QueuedMessageResponseHandler(queue);
    for (InetSocketAddress address : initialPeerAddresses) {
      mMessageSender.sendMessageWithReply(address, gkMsg, queuedMsgResponseHandler);
    }

    Collection<NodeInfo> responsivePeers = new LinkedList<>();
    for (int idx = 0; idx < initialPeerAddresses.size(); ++idx) {
      try {
        Future<KademliaMessage> future = queue.take();
        PongMessage pong = null;
        try {
          pong = (PongMessage) future.get();
          responsivePeers.add(pong.getSourceNodeInfo());
        } catch (ClassCastException e) {
          LOGGER.warn("checkKeysOfUnknownPeers() -> received message which is not pong: %s", pong);
        } catch (ExecutionException e) {
          LOGGER.error(
              "checkKeysOfUnknownPeers() -> exception happened when trying to get key from host",
              e);
        }
      } catch (InterruptedException e) {
        throw new IllegalStateException("Unexpected interrupt");
      }
    }
    addPeersToBuckets(responsivePeers);
  }

  /** TODO */
  private void clearBuckets() {
    for (List<NodeInfo> bucket : mBuckets) {
      bucket.clear();
    }
  }

  /** TODO */
  private int findIndexOfNodeInfo(List<NodeInfo> bucket, Key key) {
    int idx = 0;
    for (NodeInfo nodeInfo : bucket) {
      if (nodeInfo.getKey().equals(key)) {
        return idx;
      }
      ++idx;
    }
    return -1;
  }

  /** TODO */
  private Collection<NodeInfo> getClosestRoutingTableNodes(final Key key, int size) {
    KeyComparator keyComparator = new KeyComparator(key);
    SortedSet<Key> closestKeys = new BoundedSortedSet<>(new TreeSet<>(keyComparator), size);
    Map<Key, NodeInfo> keyInfoMap = new HashMap<>();

    for (List<NodeInfo> bucket : mBuckets) {
      synchronized (bucket) {
        for (NodeInfo nodeInfo : bucket) {
          keyInfoMap.put(nodeInfo.getKey(), nodeInfo);
          closestKeys.add(nodeInfo.getKey());
        }
      }
    }
    keyInfoMap.put(mLocalKey, getLocalNodeInfo());
    closestKeys.add(mLocalKey);

    Collection<NodeInfo> closestNodes = new ArrayList<>();
    for (Key closeKey : closestKeys) {
      closestNodes.add(keyInfoMap.get(closeKey));
    }
    LOGGER.trace("getClosestRoutingTableNodes({}, {}): {}", key, size, closestNodes);
    return closestNodes;
  }

  /** TODO */
  private NodeInfo getLocalNodeInfo() {
    synchronized (mLocalKey) {
      return new NodeInfo(mLocalKey, mLocalAddress);
    }
  }

  /** TODO */
  private List<List<NodeInfo>> initializeBuckets() {
    List<List<NodeInfo>> buckets = new ArrayList<>(Key.KEY_LENGTH);
    for (int idx = 0; idx < Key.KEY_LENGTH; ++idx) {
      buckets.add(idx, new LinkedList<NodeInfo>());
    }
    return buckets;
  }

  /** TODO */
  private GetKeyMessage prepareGetKeyMessage() {
    return new GetKeyMessage(getLocalNodeInfo());
  }

  /**
   * Add (if possible) information from sender of a message.
   *
   * @param sourceNodeInfo
   */
  /** TODO */
  private void processSenderNodeInfo(NodeInfo sourceNodeInfo) {
    LOGGER.trace("processSenderNodeInfo({})", sourceNodeInfo);
    if (sourceNodeInfo.getKey().equals(mLocalKey)) {
      return;
    }
    int distBit = getLocalKey().getDistanceBit(sourceNodeInfo.getKey());
    List<NodeInfo> bucket = mBuckets.get(distBit);
    synchronized (bucket) {
      int elementIndex = findIndexOfNodeInfo(bucket, sourceNodeInfo.getKey());
      if (elementIndex == -1) {
        if (bucket.size() < mBucketSize) {
          LOGGER.trace("processSenderNodeInfo() -> bucket.add({})", sourceNodeInfo);
          bucket.add(sourceNodeInfo);
        } else {
          NodeInfo firstNodeInfo = bucket.get(0);
          List<NodeInfo> waitingBucket = mBucketsWaitingList.get(distBit);
          if (findIndexOfNodeInfo(waitingBucket, sourceNodeInfo.getKey()) == -1) {
            waitingBucket.add(sourceNodeInfo);
            if (waitingBucket.size() == 1) {
              mMessageSender.sendMessageWithReply(firstNodeInfo.getSocketAddress(),
                  new PingMessage(getLocalNodeInfo(), firstNodeInfo), new PingCheckMessageHandler(
                      distBit));
            }
          }
        }
      } else {
        LOGGER.trace("processSenderNodeInfo() -> replaceNodeInfo({}, {})", elementIndex,
            sourceNodeInfo);
        replaceNodeInfo(bucket, elementIndex, sourceNodeInfo);
      }
    }
  }

  /**
   * Replaces node info in given bucket. Assumes we have lock on that bucket.
   *
   * @param bucket
   * @param indexToReplace
   * @param newNodeInfo
   */
  /** TODO */
  private void replaceNodeInfo(List<NodeInfo> bucket, int indexToReplace, NodeInfo newNodeInfo) {
    synchronized (bucket) {
      bucket.remove(indexToReplace);
      bucket.add(newNodeInfo);
    }
  }
}

