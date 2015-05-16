package me.gregorias.ghoul.kademlia;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
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

import me.gregorias.ghoul.kademlia.data.FindNodeMessage;
import me.gregorias.ghoul.kademlia.data.FindNodeReplyMessage;
import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.kademlia.data.KeyComparator;
import me.gregorias.ghoul.kademlia.data.NodeInfo;
import me.gregorias.ghoul.kademlia.data.PingMessage;
import me.gregorias.ghoul.kademlia.data.PongMessage;
import me.gregorias.ghoul.network.NetworkAddressDiscovery;
import me.gregorias.ghoul.security.Certificate;
import me.gregorias.ghoul.security.CertificateStorage;
import me.gregorias.ghoul.security.PersonalCertificateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Implementation of Kademlia routing protocol.
 *
 *  This implementation consists of 3 mechanisms:
 *  <ul>
 *    <li>Find node protocol - an iterative and parallel protocol described in the paper used for
 *      finding new nodes.</li>
 *    <li>Heart beat - A periodic task run in the background which searches for random key in the
 *      network. It's purpose is to refresh itself inside neighbour routing tables and handle
 *      churn.</li>
 *    <li>Node discovery - Upon being contacted by an unknown node this node may be added to
 *      routing table iff there is space in the bucket or least recently seen node in the bucket
 *      does not respond to a ping. NodeDiscovery handles this task.</li>
 *  </ul>
 *
 *  Those 3 mechanisms may require at times 3 threads to run concurrently. NodeDiscovery runs in a
 *  separate thread the entire time, HeartBeat runs periodically and uses FindNode mechanisms
 *  which uses scheduled tasks to provide timeouts events.
 *
 *  Note that many protocols use bucket size as the determinant of the size request or reply.
 *  Therefore a very low value (&lt; 3) may cause the topology graph to be inconsistent.
 */
public class KademliaRoutingImpl implements KademliaRouting {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaRoutingImpl.class);
  private static final int MINIMAL_HEART_BEAT_REQUEST_SIZE = 3;
  private static final MessageMatcher ROUTING_MATCHER = new KademliaRoutingMessageMatcher();

  private final Collection<NodeInfo> mInitialKnownPeers;
  private final KademliaRoutingTable mRoutingTable;

  /**
   * Concurrency coefficient in node lookups
   */
  private final int mAlpha;
  private final int mBucketSize;
  private final Key mLocalKey;
  private InetSocketAddress mLocalAddress;
  private final NetworkAddressDiscovery mNetworkAddressDiscovery;
  private final NewNetworkAddressObserver mNewNetworkAddressObserver;
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

  private final PersonalCertificateManager mPersonalCertificateManager;
  private final Object mPersonalPrivateKey;
  private final CertificateStorage mCertificateStorage;

  private boolean mIsRunning = false;

  /**
   * Constructs an implementation of KademliaRouting.
   *
   * @param localKey           key representing this host
   * @param networkAddressDiscovery address discovery mechanism for this peer
   * @param sender             MessageSender module for this peer
   * @param listeningService   ListeningService module for this peer
   * @param bucketSize         maximal of a single bucket
   * @param alpha              concurrency factor in find node request
   * @param initialKnownPeers  Initial peers which are used for bootstraping the routing table
   * @param messageTimeout     timeout for message
   * @param messageTimeoutUnit timeout unit
   * @param heartBeatDelay     heart beat delay
   * @param heartBeatDelayUnit heart beat delay unit
   * @param personalCertificateManager TODO
   * @param certificateStorage TODO
   * @param personalPrivateKey TODO
   * @param scheduledExecutor  executor used for executing tasks
   * @param random             random number generator
   */
  KademliaRoutingImpl(Key localKey,
                      NetworkAddressDiscovery networkAddressDiscovery,
                      MessageSender sender,
                      ListeningService listeningService,
                      int bucketSize,
                      int alpha,
                      Collection<NodeInfo> initialKnownPeers,
                      long messageTimeout,
                      TimeUnit messageTimeoutUnit,
                      long heartBeatDelay,
                      TimeUnit heartBeatDelayUnit,
                      PersonalCertificateManager personalCertificateManager,
                      CertificateStorage certificateStorage,
                      Object personalPrivateKey,
                      ScheduledExecutorService scheduledExecutor,
                      Random random) {
    assert bucketSize > 0;
    mRandom = random;
    mBucketSize = bucketSize;
    mAlpha = alpha;
    mRoutingTable = new KademliaRoutingTable(localKey, bucketSize);
    mLocalKey = localKey;
    setLocalAddress(networkAddressDiscovery.getNetworkAddress());
    mNetworkAddressDiscovery = networkAddressDiscovery;
    mNewNetworkAddressObserver = new NewNetworkAddressObserver();
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

    mPersonalCertificateManager = personalCertificateManager;
    mCertificateStorage = certificateStorage;
    mPersonalPrivateKey = personalPrivateKey;

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

      setLocalAddress(mNetworkAddressDiscovery.getNetworkAddress());
      mNetworkAddressDiscovery.addObserver(mNewNetworkAddressObserver);
      /* Connect to initial peers */
      LOGGER.trace("start(): initializeRoutingTable");
      initializeRoutingTable();

      LOGGER.trace("start(): registerListener");
      mListeningService.registerListener(ROUTING_MATCHER, mMessageListener);

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
      LOGGER.trace("stop(): Inside running lock");
      if (!mIsRunning) {
        throw new IllegalStateException("Kademlia is not running.");
      }

      mHeartBeatFuture.cancel(true);

      mNodeDiscoveryListener.put(new NodeDiscoveryTaskStopTaskEvent());
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

      boolean shouldRequestCertificate =
          !mCertificateStorage.isNodeValid(nodeInfoWithStatus.mInfo.getKey())
          || mCertificateStorage.isNodeCloseToExpiration(nodeInfoWithStatus.mInfo.getKey());

      FindNodeMessage msg = new FindNodeMessage(getLocalNodeInfo(),
          nodeInfoWithStatus.mInfo,
          messageId,
          shouldRequestCertificate,
          new ArrayList<>(),
          mSearchedKey);

      msg.signMessage(mPersonalPrivateKey);

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
      Collection<Key> keysToQuery = mAllNodes.keySet().stream().limit(mAlpha)
          .collect(Collectors.toList());
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
      for (Integer listenerId : mRegisteredListeners.values()) {
        unregisterFindNodeTaskEventListener(listenerId, mEventQueue);
      }
      mRegisteredListeners.clear();
    }
  }

  private enum FindNodeStatus {
    QUERIED,
    TIMED_OUT,
    PENDING,
    UNQUERIED
  }

  private interface FindNodeTaskEvent { }

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
    private final int mRequestSize = Math.max(mBucketSize, MINIMAL_HEART_BEAT_REQUEST_SIZE);

    @Override
    public void run() {
      LOGGER.trace("HeartBeatTask.run()");
      mReadRunningLock.lock();
      try {
        if (!mIsRunning) {
          return;
        }
        Key randomKey = Key.newRandomKey(mRandom);
        findClosestNodes(randomKey, mRequestSize);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        mReadRunningLock.unlock();
      }
      LOGGER.trace("HeartBeatTask.run() -> void");
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

      boolean shouldRequestCertificate = !mCertificateStorage.isNodeValid(targetNode.getKey())
          || mCertificateStorage.isNodeCloseToExpiration(targetNode.getKey());

      PingMessage pingMessage = new PingMessage(getLocalNodeInfo(),
          targetNode,
          pingId,
          shouldRequestCertificate,
          new ArrayList<>());

      pingMessage.signMessage(mPersonalPrivateKey);
      mMessageSender.sendMessage(targetNode.getSocketAddress(), pingMessage);

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

  private interface NodeDiscoveryTaskEvent { }

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
    public void receive(KademliaMessage msg) {
      if (isMessageFromValidHost(msg)) {
        addCandidate(msg.getSourceNodeInfo());
      }
      if (msg instanceof FindNodeMessage) {
        receiveFindNodeMessage((FindNodeMessage) msg);
      } else if (msg instanceof FindNodeReplyMessage) {
        receiveFindNodeReplyMessage((FindNodeReplyMessage) msg);
      } else if (msg instanceof PingMessage) {
        receivePingMessage((PingMessage) msg);
      } else if (msg instanceof PongMessage) {
        receivePongMessage((PongMessage) msg);
      } else {
        LOGGER.error("Received unknown message.");
      }
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

    private boolean isMessageFromValidHost(KademliaMessage msg) {
      NodeInfo sourceNodeInfo = msg.getSourceNodeInfo();
      if (!mCertificateStorage.isNodeValid(sourceNodeInfo.getKey())) {
        Collection<Certificate> certificates = msg.getCertificates();
        if (certificates.size() == 0) {
          return false;
        }
        mCertificateStorage.addCertificates(certificates);
        return mCertificateStorage.isNodeValid(sourceNodeInfo.getKey());
      } else {
        return true;
      }
    }

    private void receiveFindNodeMessage(FindNodeMessage msg) {
      LOGGER.trace("receiveFindNodeMessage({})", msg);
      Collection<NodeInfo> foundNodes = mRoutingTable.getClosestNodes(msg.getSearchedKey(),
          mBucketSize);

      Key destinationKey = msg.getSourceNodeInfo().getKey();
      boolean shouldRequireCertificates = !mCertificateStorage.isNodeValid(destinationKey)
          || mCertificateStorage.isNodeCloseToExpiration(destinationKey);
      Collection<Certificate> personalCertificates = new ArrayList<>();
      if (msg.isCertificateRequest()) {
        personalCertificates.addAll(mPersonalCertificateManager.getPersonalCertificates());
      }

      FindNodeReplyMessage replyMessage = new FindNodeReplyMessage(getLocalNodeInfo(),
          msg.getSourceNodeInfo(),
          msg.getId(),
          shouldRequireCertificates,
          personalCertificates,
          foundNodes);
      replyMessage.signMessage(mPersonalPrivateKey);
      mMessageSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(), replyMessage);
    }

    private void receiveFindNodeReplyMessage(FindNodeReplyMessage msg) {
      LOGGER.trace("receiveFindNodeReplyMessage({})", msg);
      if (!mCertificateStorage.isNodeValid(msg.getSourceNodeInfo().getKey())) {
        LOGGER.info("receiveFindNodeReplyMessage({}): Received reply from invalid node.", msg);
        return;
      }

      Optional<Object> pubKey = mCertificateStorage.getPublicKey(msg.getSourceNodeInfo().getKey());
      if (!pubKey.isPresent()) {
        return;
      }

      if (!msg.verifyMessage(pubKey.get())) {
        LOGGER.info("receiveFindNodeReplyMessage({}): Received message has invalid signature.",
            msg);
        return;
      }

      notifyFindNodeTaskEventListeners(msg);

      if (msg.isCertificateRequest()) {
        PongMessage pongMessage = new PongMessage(getLocalNodeInfo(),
            msg.getSourceNodeInfo(),
            msg.getId(),
            false,
            mPersonalCertificateManager.getPersonalCertificates());
        pongMessage.signMessage(mPersonalPrivateKey);
        mMessageSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(), pongMessage);
      }
    }

    private void receivePingMessage(PingMessage msg) {
      LOGGER.trace("receivePingMessage({})", msg);
      Key destinationKey = msg.getSourceNodeInfo().getKey();
      boolean shouldRequireCertificates = !mCertificateStorage.isNodeValid(destinationKey)
          || mCertificateStorage.isNodeCloseToExpiration(destinationKey);
      Collection<Certificate> personalCertificates = new ArrayList<>();
      if (msg.isCertificateRequest()) {
        personalCertificates.addAll(mPersonalCertificateManager.getPersonalCertificates());
      }

      PongMessage pongMessage = new PongMessage(getLocalNodeInfo(),
          msg.getSourceNodeInfo(),
          msg.getId(),
          shouldRequireCertificates,
          personalCertificates);
      pongMessage.signMessage(mPersonalPrivateKey);
      mMessageSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(), pongMessage);
    }

    private void receivePongMessage(PongMessage msg) {
      LOGGER.trace("receivePongMessage({})", msg);
      if (!mCertificateStorage.isNodeValid(msg.getSourceNodeInfo().getKey())) {
        LOGGER.info("receivePongMessage({}): Received reply from invalid node.", msg);
        return;
      }

      Optional<Object> pubKey = mCertificateStorage.getPublicKey(msg.getSourceNodeInfo().getKey());
      if (!pubKey.isPresent()) {
        return;
      }

      if (!msg.verifyMessage(pubKey.get())) {
        LOGGER.info("receivePongMessage({}): Received message has invalid signature.", msg);
        return;
      }

      NodeDiscoveryTaskEvent event = new NodeDiscoveryTaskPongEvent(msg);
      mNodeDiscoveryListener.add(event);

      if (msg.isCertificateRequest()) {
        PongMessage pongMessage = new PongMessage(getLocalNodeInfo(),
            msg.getSourceNodeInfo(),
            msg.getId(),
            false,
            mPersonalCertificateManager.getPersonalCertificates());
        pongMessage.signMessage(mPersonalPrivateKey);
        mMessageSender.sendMessage(msg.getSourceNodeInfo().getSocketAddress(), pongMessage);
      }
    }
  }

  private class NewNetworkAddressObserver implements Observer {
    @Override
    public void update(Observable observable, Object arg) {
      InetSocketAddress newAddress = (InetSocketAddress) arg;
      setLocalAddress(newAddress);
    }
  }

  private static class KademliaRoutingMessageMatcher implements MessageMatcher {
    @Override
    public boolean match(KademliaMessage msg) {
      return msg instanceof PingMessage
          || msg instanceof PongMessage
          || msg instanceof FindNodeMessage
          || msg instanceof FindNodeReplyMessage;
    }
  }

  private synchronized NodeInfo getLocalNodeInfo() {
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
        mNeighbourListener.get().notifyAboutNewNeighbour(newNodeInfo);
      }
    } finally {
      mNeighbourListenerLock.unlock();
    }
  }

  private synchronized void setLocalAddress(InetSocketAddress localAddress) {
    mLocalAddress = localAddress;
  }
}

