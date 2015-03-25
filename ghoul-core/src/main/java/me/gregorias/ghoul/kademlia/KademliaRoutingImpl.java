package me.gregorias.ghoul.kademlia;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Math.min;

/** Implementation of Kademlia routing */
class KademliaRoutingImpl implements KademliaRouting {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaRoutingImpl.class);

  private final Collection<NodeInfo> mInitialKnownPeers;
  private final KademliaRoutingTable mRoutingTable;

  /**
   * Concurrency coefficient in node lookups
   */
  private final int mAlpha;
  private final int mBucketSize;
  private final Key mLocalKey;
  private InetSocketAddress mLocalAddress;
  private final MessageSender mMessageSender;
  private final ListeningService mListeningService;
  private final MessageListener mMessageListener;


  /**
   * Map from expected message id to a collection of FindNodeTaskEvent queues expecting response
   * on that id.
   */
  private final Map<Integer, Collection<BlockingQueue<FindNodeTaskEvent>>> mFindNodeListeners;

  private Future<?> mHeartBeatFuture = null;
  private final long mHeartBeatDelay;
  private final TimeUnit mHeartBeatDelayUnit;

  private BlockingQueue<NodeDiscoveryTaskEvent> mNodeDiscoveryListener;
  private Future<?> mNodeDiscoveryFuture = null;

  private Optional<NeighbourListener> mNeighbourListener;
  private final Lock mNeighbourListenerLock;

  private final long mMessageTimeout;
  private final TimeUnit mMessageTimeoutUnit;

  private final ScheduledExecutorService mScheduledExecutor;
  private final Random mRandom;

  private final Lock mReadRunningLock;
  private final Lock mWriteRunningLock;

  private boolean mIsRunning = false;

  /**
   * Constructs an implementation of KademliaRouting.
   *
   * @param localNodeInfo      information about node represented by this peer
   * @param sender             MessageSender module for this peer
   * @param listeningService   ListeningService module for this peer
   * @param bucketSize         maximal of a single bucket
   * @param alpha              concurrency factor in find node request
   * @param initialKnownPeers  Initial peers which are used for bootstraping the routing table
   * @param messageTimeout     timeout for message
   * @param messageTimeoutUnit timeout unit
   * @param heartBeatDelay     heart beat delay
   * @param heartBeatDelayUnit heart beat delay unit
   * @param scheduledExecutor  executor used for executing tasks
   * @param random             random number generator
   */
  KademliaRoutingImpl(NodeInfo localNodeInfo,
                      MessageSender sender,
                      ListeningService listeningService,
                      int bucketSize,
                      int alpha,
                      Collection<NodeInfo> initialKnownPeers,
                      long messageTimeout,
                      TimeUnit messageTimeoutUnit,
                      long heartBeatDelay,
                      TimeUnit heartBeatDelayUnit,
                      ScheduledExecutorService scheduledExecutor,
                      Random random) {
    assert bucketSize > 0;
    mRandom = random;
    mBucketSize = bucketSize;
    mAlpha = alpha;
    mRoutingTable = new KademliaRoutingTable(localNodeInfo.getKey(), bucketSize);
    mLocalKey = localNodeInfo.getKey();
    mLocalAddress = localNodeInfo.getSocketAddress();
    mMessageSender = sender;
    mListeningService = listeningService;
    mMessageListener = new MessageListenerImpl();

    mNeighbourListener = Optional.empty();
    mNeighbourListenerLock = new ReentrantLock();

    mFindNodeListeners = new HashMap<>();
    mNodeDiscoveryListener = new LinkedBlockingQueue<>();

    mMessageTimeout = messageTimeout;
    mMessageTimeoutUnit = messageTimeoutUnit;

    mHeartBeatDelay = heartBeatDelay;
    mHeartBeatDelayUnit = heartBeatDelayUnit;

    mScheduledExecutor = scheduledExecutor;

    mInitialKnownPeers = new LinkedList<>(initialKnownPeers);

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
  public Collection<NodeInfo> getFlatRoutingTable() {
    return mRoutingTable.flatten();
  }

  public boolean isRunning() {
    mReadRunningLock.lock();
    boolean isRunning = mIsRunning;
    mReadRunningLock.unlock();
    return isRunning;
  }

  @Override
  public void registerNeighbourListener(NeighbourListener listener) {
    mNeighbourListenerLock.lock();
    try {
      mNeighbourListener = Optional.of(listener);
    } finally {
      mNeighbourListenerLock.unlock();
    }
  }

  @Override
  public void unregisterNeighbourListener() {
    mNeighbourListenerLock.lock();
    try {
      mNeighbourListener = Optional.empty();
    } finally {
      mNeighbourListenerLock.unlock();
    }
  }

  @Override
  public void start() throws KademliaException {
    LOGGER.debug("start()");
    mWriteRunningLock.lock();
    try {
      if (mIsRunning) {
        throw new IllegalStateException("Kademlia has already started.");
      }

      /* Connect to initial peers */
      LOGGER.trace("start(): initializeRoutingTable");
      initializeRoutingTable();

      LOGGER.trace("start(): registerListener");
      mListeningService.registerListener(mMessageListener);

      NodeDiscoveryTask nodeDiscoveryTask = new NodeDiscoveryTask();
      mNodeDiscoveryFuture = mScheduledExecutor.submit(nodeDiscoveryTask);

      mHeartBeatFuture = mScheduledExecutor.scheduleWithFixedDelay(new HeartBeatTask(),
          0,
          mHeartBeatDelay,
          mHeartBeatDelayUnit);

      mIsRunning = true;
    } finally {
      mWriteRunningLock.unlock();
    }
  }

  @Override
  public void stop() throws KademliaException {
    LOGGER.debug("stop()");
    mWriteRunningLock.lock();
    try {
      mHeartBeatFuture.cancel(true);

      mNodeDiscoveryListener.put(new NodeDiscoveryTaskStopTaskEvent());
      LOGGER.trace("stop(): Inside lock");
      if (!mIsRunning) {
        throw new IllegalStateException("Kademlia is not running.");
      }
      mNodeDiscoveryFuture.get();

      mListeningService.unregisterListener(mMessageListener);
      mIsRunning = false;
    } catch (ExecutionException | InterruptedException e) {
      LOGGER.error("stop(): Unexpected exception.", e);
    } finally {
      mWriteRunningLock.unlock();
    }
    LOGGER.info("stop() -> void");
  }

  @Override
  public String toString() {
    return String.format("KademliaRoutingImpl{mLocalKey:%s}", mLocalKey);
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
      LOGGER.debug("FindNodeTask.call(): key = {}", mSearchedKey);
      mReadRunningLock.lock();
      try {
        if (!mIsRunning) {
          throw new IllegalStateException("Called findClosestNodes() on non-running kademlia.");
        }

        mAllNodes = initializeAllNodes();
        sendRequestsToInitialCandidates();

        // While there are pending nodes wait for replies and timeouts. Send new messages when
        // possible candidates shows up
        while (getPendingNodes().size() > 0) {
          FindNodeTaskEvent event = mEventQueue.take();
          LOGGER.trace("FindNodeTask.call(): key = {}, received event: {}", mSearchedKey, event);
          if (event instanceof FindNodeTaskReplyEvent) {
            handleReply(((FindNodeTaskReplyEvent) event).mMsg);
          } else if (event instanceof FindNodeTaskTimeoutEvent) {
            handleTimeOut(((FindNodeTaskTimeoutEvent) event).mTimedOutNodeInfo);
          } else {
            throw new IllegalStateException("Unexpected event has been received.");
          }

          sendQueriesToValidCandidates();
        }

        unregisterAllEventListenersForThisTask();
        mEventQueue.clear();
        LOGGER.debug("FindNodeTask.call() -> returning found nodes.");
        return getQueriedNodes().stream().limit(mResponseSize).collect(Collectors.toList());
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

      @Override
      public String toString() {
        return String.format("NodeInfoWithStatus{mInfo:%s, mStatus:%s}", mInfo, mStatus);
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
      LOGGER.trace("FindNodeTask.handleReply({})", replyMessage);
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
      LOGGER.trace("FindNodeTask.handleTimeout({})", nodeInfo);
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
      Collection<NodeInfo> closeNodes = mRoutingTable.getClosestNodes(mSearchedKey, mResponseSize);
      SortedMap<Key, NodeInfoWithStatus> allNodes = new TreeMap<>(new KeyComparator(mSearchedKey));
      closeNodes.stream().
          forEach((NodeInfo info) -> allNodes.put(info.getKey(), new NodeInfoWithStatus(info)));

      NodeInfoWithStatus localNodeInfo = new NodeInfoWithStatus(getLocalNodeInfo());
      localNodeInfo.mStatus = FindNodeStatus.QUERIED;
      allNodes.put(mLocalKey, localNodeInfo);
      return allNodes;
    }

    private void sendFindNodeMessage(NodeInfoWithStatus nodeInfoWithStatus) {
      LOGGER.trace("FindNodeTask.sendFindNodeMessage({})", nodeInfoWithStatus);
      assert nodeInfoWithStatus.mStatus == FindNodeStatus.UNQUERIED;

      int messageId = registerFindNodeTaskEventListener(mEventQueue);
      mRegisteredListeners.put(nodeInfoWithStatus.mInfo.getKey(), messageId);

      FindNodeMessage msg = new FindNodeMessage(getLocalNodeInfo(),
          nodeInfoWithStatus.mInfo,
          messageId,
          mSearchedKey);

      mMessageSender.sendMessage(nodeInfoWithStatus.mInfo.getSocketAddress(), msg);
      mScheduledExecutor.schedule(() -> {
          boolean hasPutSuccessfully = false;
          while (!hasPutSuccessfully) {
            try {
              mEventQueue.put(new FindNodeTaskTimeoutEvent(nodeInfoWithStatus.mInfo));
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
      LOGGER.trace("FindNodeTask.sendRequestsToInitialCandidates()");
      Collection<Key> keysToQuery = mAllNodes.keySet().stream().
          limit(min(mResponseSize, mAlpha)).
          collect(Collectors.toList());
      for (Key keyToQuery : keysToQuery) {
        NodeInfoWithStatus status = mAllNodes.get(keyToQuery);
        if (status.mStatus == FindNodeStatus.UNQUERIED) {
          sendFindNodeMessage(status);
        }
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

  private static class FindNodeTaskReplyEvent implements FindNodeTaskEvent {
    public final FindNodeReplyMessage mMsg;

    public FindNodeTaskReplyEvent(FindNodeReplyMessage msg) {
      mMsg = msg;
    }
  }

  private static class FindNodeTaskTimeoutEvent implements FindNodeTaskEvent {
    public final NodeInfo mTimedOutNodeInfo;

    public FindNodeTaskTimeoutEvent(NodeInfo timedOutNodeInfo) {
      mTimedOutNodeInfo = timedOutNodeInfo;
    }
  }

  /**
   * Notify pertinent find node listeners about find node reply message.
   *
   * @param msg received reply message
   */
  private void notifyFindNodeTaskEventListeners(FindNodeReplyMessage msg) {
    synchronized (mFindNodeListeners) {
      Collection<BlockingQueue<FindNodeTaskEvent>> listeners = mFindNodeListeners.get(msg.getId());
      try {
        if (listeners != null) {
          FindNodeTaskEvent event = new FindNodeTaskReplyEvent(msg);
          for (BlockingQueue<FindNodeTaskEvent> queue : listeners) {
            queue.put(event);
          }
        }
      } catch (InterruptedException e) {
        LOGGER.error("notifyFindNodeTaskEventListeners(): unexpected InterruptedException.", e);
      }
    }
  }

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

  /* Heart beat protocol implementation */
  private class HeartBeatTask implements Runnable {

    @Override
    public void run() {
      LOGGER.trace("HeartBeatTask.run()");
      mReadRunningLock.lock();
      try {
        if (!mIsRunning) {
          return;
        }
        Key randomKey = Key.newRandomKey(mRandom);
        findClosestNodes(randomKey);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        mReadRunningLock.unlock();
      }
    }
  }

  /* End of heart beat protocol implementation */

  /* Node discovery protocol implementation */

  /**
   * Task which updates routing table based on incoming messages.
   *
   * On receipt of a new candidate it puts it into the bucket if there is space. Otherwise it pings
   * least recently seen node in the corresponding bucket. If node responds then the candidate is
   * removed, on time out the candidate replaces the pinged node. There can be only
   * one candidate per bucket at the same time.
   *
   * This class implements actor-like semantics of operation.
   */
  private class NodeDiscoveryTask implements Runnable {
    private final BlockingQueue<NodeDiscoveryTaskEvent> mInputQueue;
    private final List<Optional<NodeInfo>> mCandidates;
    private final ExpectedMessageIds mExpectedIds;

    public NodeDiscoveryTask() {
      mInputQueue = mNodeDiscoveryListener;
      mCandidates = new ArrayList<>();
      for (int i = 0; i < Key.KEY_LENGTH; ++i) {
        mCandidates.add(Optional.empty());
      }
      mExpectedIds = new ExpectedMessageIds();
    }

    @Override
    public void run() {
      LOGGER.trace("NodeDiscoveryTask.run()");
      boolean hasStopped = false;

      try {
        while (!hasStopped) {
          NodeDiscoveryTaskEvent event = mInputQueue.take();
          if (event instanceof NodeDiscoveryTaskAddNodeEvent) {
            handleAddNode((NodeDiscoveryTaskAddNodeEvent) event);
          } else if (event instanceof NodeDiscoveryTaskPongEvent) {
            handleReply((NodeDiscoveryTaskPongEvent) event);
          } else if (event instanceof NodeDiscoveryTaskTimeoutEvent) {
            handleTimeout((NodeDiscoveryTaskTimeoutEvent) event);
          } else if (event instanceof NodeDiscoveryTaskStopTaskEvent) {
            hasStopped = true;
          } else {
            LOGGER.error("NodeDiscoveryTask.run(): unknown event type: {}.", event);
          }
        }
      } catch (InterruptedException e) {
        LOGGER.error("NodeDiscoveryTask.run(): Unexpected interrupt happened.", e);
      }
      LOGGER.debug("NodeDiscoveryTask.run() -> void");
    }

    /**
     * A Map wrapper which holds expected message IDs and their keys.
     */
    private class ExpectedMessageIds {
      private final Map<Integer, Collection<Key>> mMessageIdToKeys = new HashMap<>();

      public void addExpectedMessage(int id, Key destination) {
        if (!mMessageIdToKeys.containsKey(id)) {
          mMessageIdToKeys.put(id, new ArrayList<>());
        }
        mMessageIdToKeys.get(id).add(destination);
      }

      public void deleteExpectedMessage(int id, Key destination) {
        if (mMessageIdToKeys.containsKey(id)) {
          mMessageIdToKeys.get(id).remove(destination);
          if (mMessageIdToKeys.get(id).isEmpty()) {
            mMessageIdToKeys.remove(id);
          }
        }
      }

      public boolean isMessageExpected(int id, Key source) {
        return mMessageIdToKeys.containsKey(id) && mMessageIdToKeys.get(id).contains(source);
      }
    }

    private void handleAddNode(NodeDiscoveryTaskAddNodeEvent event) {
      LOGGER.trace("NodeDiscoveryTask.handleAddNode({})", event.toString());
      NodeInfo newNodeInfo = event.mNodeInfo;
      if (!mRoutingTable.contains(newNodeInfo.getKey())) {
        int distanceBit = mLocalKey.getDistanceBit(newNodeInfo.getKey());
        if (mRoutingTable.isBucketFull(distanceBit)) {
          if (mCandidates.get(distanceBit).isPresent()) {
            LOGGER.trace("NodeDiscoveryTask.handleAddNode({}) -> a candidate for this bucket is"
                + " already present.", event);
          } else {
            mCandidates.set(distanceBit, Optional.of(newNodeInfo));
            Optional<NodeInfo> oldestNode = mRoutingTable.getLeastRecentlyAccessedNode(distanceBit);
            assert oldestNode.isPresent();
            sendPingMessage(oldestNode.get());
          }
        } else {
          mRoutingTable.add(newNodeInfo);
          notifyNeighbourListener(newNodeInfo);
        }
      } else {
        mRoutingTable.updateNode(newNodeInfo);

      }
    }

    private void handleReply(NodeDiscoveryTaskPongEvent event) {
      LOGGER.trace("NodeDiscoveryTask.handleReply({})", event);
      PongMessage pong = event.mMsg;
      if (mExpectedIds.isMessageExpected(pong.getId(), pong.getSourceNodeInfo().getKey())) {
        mExpectedIds.deleteExpectedMessage(pong.getId(), pong.getSourceNodeInfo().getKey());
        int distanceBit = mLocalKey.getDistanceBit(pong.getSourceNodeInfo().getKey());
        mCandidates.set(distanceBit, Optional.empty());
      } // else time out was first.
    }

    private void handleTimeout(NodeDiscoveryTaskTimeoutEvent event) {
      LOGGER.trace("NodeDiscoveryTask.handleTimeout({})", event);
      if (mExpectedIds.isMessageExpected(event.mPingId, event.mTimedOutNodeInfo.getKey())) {
        mExpectedIds.deleteExpectedMessage(event.mPingId, event.mTimedOutNodeInfo.getKey());
        int distanceBit = mLocalKey.getDistanceBit(event.mTimedOutNodeInfo.getKey());
        Optional<NodeInfo> candidate = mCandidates.set(distanceBit, Optional.empty());
        if (!candidate.isPresent()) {
          LOGGER.error("NodeDiscoveryTask.handleTimeout(): Unexpected lack of candidate.");
        } else {
          mRoutingTable.replaceNode(event.mTimedOutNodeInfo, candidate.get());
          notifyNeighbourListener(candidate.get());
        }
      }
    }

    private void sendPingMessage(NodeInfo targetNode) {
      int pingId = mRandom.nextInt();
      mMessageSender.sendMessage(targetNode.getSocketAddress(),
          new PingMessage(getLocalNodeInfo(), targetNode, pingId));
      mExpectedIds.addExpectedMessage(pingId, targetNode.getKey());
      mScheduledExecutor.schedule(() -> {
          boolean hasPutSuccessfully = false;
          NodeDiscoveryTaskTimeoutEvent timeoutEvent =
              new NodeDiscoveryTaskTimeoutEvent(targetNode, pingId);

          while (!hasPutSuccessfully) {
            try {
              mNodeDiscoveryListener.put(timeoutEvent);
              hasPutSuccessfully = true;
            } catch (InterruptedException e) {
              LOGGER.error("TimeoutHandler(): Unexpected interrupt", e);
            }
          }
        },
          mMessageTimeout,
          mMessageTimeoutUnit);
    }
  }

  private static interface NodeDiscoveryTaskEvent { }

  private static class NodeDiscoveryTaskAddNodeEvent implements NodeDiscoveryTaskEvent {
    private final NodeInfo mNodeInfo;

    public NodeDiscoveryTaskAddNodeEvent(NodeInfo nodeInfo) {
      mNodeInfo = nodeInfo;
    }

    @Override
    public String toString() {
      return String.format("NodeDiscoveryTaskAddNodeEvent{mNodeInfo:%s}", mNodeInfo);
    }
  }

  private static class NodeDiscoveryTaskPongEvent implements NodeDiscoveryTaskEvent {
    public final PongMessage mMsg;

    public NodeDiscoveryTaskPongEvent(PongMessage msg) {
      mMsg = msg;
    }

    @Override
    public String toString() {
      return String.format("NodeDiscoveryTaskPongEvent{mMsg:%s}", mMsg);
    }
  }

  private static class NodeDiscoveryTaskStopTaskEvent implements NodeDiscoveryTaskEvent { }

  private static class NodeDiscoveryTaskTimeoutEvent implements NodeDiscoveryTaskEvent {
    public final NodeInfo mTimedOutNodeInfo;
    public final int mPingId;

    public NodeDiscoveryTaskTimeoutEvent(NodeInfo timedOutNodeInfo, int pingId) {
      mTimedOutNodeInfo = timedOutNodeInfo;
      mPingId = pingId;
    }

    @Override
    public String toString() {
      return String.format("NodeDiscoveryTaskTimeoutEvent{mTimedOutNodeInfo:%s, mPingId:%d}",
          mTimedOutNodeInfo,
          mPingId);
    }
  }

  /* End of node discovery implementation */

  /**
   * Handler for incoming kademlia messages.
   */
  private class MessageListenerImpl implements MessageListener {
    @Override
    public void receiveFindNodeMessage(FindNodeMessage msg) {
      LOGGER.trace("receiveFindNodeMessage({})", msg);
      addCandidate(msg.getSourceNodeInfo());
      Collection<NodeInfo> foundNodes = mRoutingTable.getClosestNodes(msg.getSearchedKey(),
          mBucketSize);
      mMessageSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(),
          new FindNodeReplyMessage(getLocalNodeInfo(),
              msg.getSourceNodeInfo(),
              msg.getId(),
              foundNodes));
    }

    @Override
    public void receiveFindNodeReplyMessage(FindNodeReplyMessage msg) {
      LOGGER.trace("receiveFindNodeReplyMessage({})", msg);
      addCandidate(msg.getSourceNodeInfo());
      notifyFindNodeTaskEventListeners(msg);
    }

    @Override
    public void receivePingMessage(PingMessage msg) {
      LOGGER.trace("receivePingMessage({})", msg);
      addCandidate(msg.getSourceNodeInfo());
      PongMessage pongMessage = new PongMessage(getLocalNodeInfo(),
          msg.getSourceNodeInfo(),
          msg.getId());
      mMessageSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(), pongMessage);
    }

    @Override
    public void receivePongMessage(PongMessage msg) {
      LOGGER.trace("receivePongMessage({})", msg);
      addCandidate(msg.getSourceNodeInfo());
      NodeDiscoveryTaskEvent event = new NodeDiscoveryTaskPongEvent(msg);
      mNodeDiscoveryListener.add(event);
    }

    @Override
    public String toString() {
      return String.format("KademliaRoutingImpl.MessageListenerImpl{super:%s}",
          KademliaRoutingImpl.this);
    }

    private void addCandidate(NodeInfo sourceNodeInfo) {
      NodeDiscoveryTaskEvent event = new NodeDiscoveryTaskAddNodeEvent(sourceNodeInfo);
      try {
        mNodeDiscoveryListener.put(event);
      } catch (InterruptedException e) {
        LOGGER.error("addCandidate(): Unexpected interruptedException.", e);
      }
    }
  }

  private NodeInfo getLocalNodeInfo() {
    return new NodeInfo(mLocalKey, mLocalAddress);
  }

  private void initializeRoutingTable() {
    mRoutingTable.clear();
    mRoutingTable.addAll(mInitialKnownPeers);
  }

  private void notifyNeighbourListener(NodeInfo newNodeInfo) {
    mNeighbourListenerLock.lock();
    try {
      if (mNeighbourListener.isPresent()) {
        mNeighbourListener.get().notifyNewNeighbour(newNodeInfo);
      }
    } finally {
      mNeighbourListenerLock.unlock();
    }
  }
}

