package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.FindNodeMessage;
import me.gregorias.ghoul.kademlia.data.FindNodeReplyMessage;
import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.kademlia.data.NodeInfo;
import me.gregorias.ghoul.kademlia.data.PingMessage;
import me.gregorias.ghoul.kademlia.data.PongMessage;
import me.gregorias.ghoul.network.local.LocalMessaging;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class KademliaRoutingBasicTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaRoutingBasicTest.class);
  private static final Random RANDOM = new Random();
  private static final long MESSAGE_TIMEOUT = 1;
  private static final TimeUnit MESSAGE_TIMEOUT_UNIT = TimeUnit.SECONDS;
  private KademliaRoutingBuilder mBuilder = null;
  private LocalMessaging mLocalMessaging;
  private MessageSender mLocalSender;
  private InetSocketAddress mLocalAddress;

  @Rule
  public Timeout mGlobalTimeout = new Timeout(5, TimeUnit.SECONDS);

  @Before
  public void setUp() throws KademliaException {
    mLocalMessaging = new LocalMessaging();

    ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(3);

    mBuilder = new KademliaRoutingBuilder(RANDOM);
    mLocalAddress = new InetSocketAddress(0);

    mBuilder.setByteListeningService(mLocalMessaging.getByteListeningService(0));
    mBuilder.setByteSender(mLocalMessaging.getByteSender(0));
    mLocalSender = new MessageSenderAdapter(mLocalMessaging.getByteSender(0));
    mBuilder.setExecutor(scheduledExecutor);
    mBuilder.setLocalAddress(mLocalAddress);
    mBuilder.setMessageTimeout(MESSAGE_TIMEOUT, MESSAGE_TIMEOUT_UNIT);
  }

  @Test
  public void kademliaPeersShouldFindEachOther() throws KademliaException, InterruptedException {
    LOGGER.info("kademliaPeersShouldFindEachOther()");
    Key key0 = new Key(0);
    Key key1 = new Key(1);
    mBuilder.setKey(key0);
    KademliaRouting kademlia0 = mBuilder.createPeer();
    Collection<NodeInfo> peerInfos = new ArrayList<>();
    peerInfos.add(new NodeInfo(key0, mLocalAddress));
    mBuilder.setInitialPeersWithKeys(peerInfos);
    mBuilder.setKey(key1);
    KademliaRouting kademlia1 = mBuilder.createPeer();

    BlockingQueue<NodeInfo> foundNeighbours = new LinkedBlockingQueue<>();
    NeighbourListener neighbourListener = new QueueingNeighbourListener(foundNeighbours);
    kademlia0.registerNeighbourListener(neighbourListener);

    kademlia0.start();
    kademlia1.start();

    Collection<NodeInfo> foundZeros = kademlia1.findClosestNodes(key0);

    boolean foundZero = false;
    for (NodeInfo nodeInfo : foundZeros) {
      if (nodeInfo.getKey().equals(key1)) {
        foundZero = true;
      }
    }
    assertTrue(foundZero);

    foundNeighbours.take();
    Collection<NodeInfo> foundOnes = kademlia0.findClosestNodes(key1);
    boolean foundOne = false;
    for (NodeInfo nodeInfo : foundOnes) {
      if (nodeInfo.getKey().equals(key1)) {
        foundOne = true;
      }
    }
    assertTrue(foundOne);

    kademlia1.stop();
    kademlia0.stop();
  }

  @Test
  public void kademliaPeersShouldFindItSelfWhenLookingForItself() throws KademliaException,
      InterruptedException {
    Key key0 = new Key(0);
    mBuilder.setKey(key0);
    KademliaRouting kademlia = mBuilder.createPeer();
    kademlia.start();

    Collection<NodeInfo> foundNodes = kademlia.findClosestNodes(key0);
    assertEquals(1, foundNodes.size());
    for (NodeInfo nodeInfo : foundNodes) {
      assertEquals(key0, nodeInfo.getKey());
    }
    kademlia.stop();
  }

  @Test
  public void kademliaPeersShouldFindItSelfWhenLookingForOther() throws KademliaException,
      InterruptedException {
    Key key0 = new Key(0);
    Key key10 = new Key(10);
    mBuilder.setKey(key0);
    KademliaRouting kademlia = mBuilder.createPeer();
    kademlia.start();

    Collection<NodeInfo> foundNodes = kademlia.findClosestNodes(key10);
    assertEquals(1, foundNodes.size());
    for (NodeInfo nodeInfo : foundNodes) {
      assertEquals(key0, nodeInfo.getKey());
    }
    kademlia.stop();
  }

  @Test
  public void kademliaPeersShouldFindSoughtNode() throws KademliaException, InterruptedException {
    Key key0 = new Key(0);
    Key key1 = new Key(1);
    Key key2 = new Key(2);
    mBuilder.setKey(key0);
    KademliaRouting kademlia0 = mBuilder.createPeer();
    Collection<NodeInfo> peerInfos = new ArrayList<>();
    peerInfos.add(new NodeInfo(key0, mLocalAddress));
    mBuilder.setInitialPeersWithKeys(peerInfos);
    mBuilder.setKey(key1);
    KademliaRouting kademlia1 = mBuilder.createPeer();
    mBuilder.setKey(key2);
    KademliaRouting kademlia2 = mBuilder.createPeer();

    kademlia0.start();
    kademlia1.start();
    kademlia2.start();

    kademlia1.findClosestNodes(key0);
    kademlia2.findClosestNodes(key0);

    Collection<NodeInfo> foundNodes = kademlia0.findClosestNodes(key2);
    boolean hasFound2 = false;
    for (NodeInfo nodeInfo : foundNodes) {
      if (nodeInfo.getKey().equals(key2)) {
        hasFound2 = true;
      }
    }
    assertTrue(hasFound2);
    kademlia2.stop();
    kademlia1.stop();
    kademlia0.stop();
  }

  @Test
  public void kademliaPeersShouldStartAndStopMultipleTimes() throws KademliaException,
      InterruptedException {
    Key key0 = new Key(0);
    mBuilder.setKey(key0);
    KademliaRouting kademlia = mBuilder.createPeer();
    kademlia.start();

    Collection<NodeInfo> foundNodes = kademlia.findClosestNodes(key0);
    assertEquals(1, foundNodes.size());
    for (NodeInfo nodeInfo : foundNodes) {
      assertEquals(key0, nodeInfo.getKey());
    }
    kademlia.stop();

    kademlia.start();

    foundNodes = kademlia.findClosestNodes(key0);
    assertEquals(1, foundNodes.size());
    for (NodeInfo nodeInfo : foundNodes) {
      assertEquals(key0, nodeInfo.getKey());
    }
    kademlia.stop();

    kademlia.start();

    foundNodes = kademlia.findClosestNodes(key0);
    assertEquals(1, foundNodes.size());
    for (NodeInfo nodeInfo : foundNodes) {
      assertEquals(key0, nodeInfo.getKey());
    }
    kademlia.stop();
  }

  /**
   * Kademlia with key 1 should send a heart beat to kademlia with key 0 effectively advertising
   * itself and adding to its routing table.
   *
   * @throws KademliaException
   * @throws InterruptedException
   */
  @Test
  public void shouldSendPeriodicHeartBeat() throws KademliaException, InterruptedException {
    final long heartBeat = 50;
    final TimeUnit heartBeatUnit = TimeUnit.MILLISECONDS;

    Key key0 = new Key(0);
    Key key1 = new Key(1);

    Collection<NodeInfo> peerInfos = new ArrayList<>();
    peerInfos.add(new NodeInfo(key0, mLocalAddress));

    mBuilder.setKey(key0);
    KademliaRouting kademlia0 = mBuilder.createPeer();
    mBuilder.setKey(key1);
    mBuilder.setHeartBeatDelay(heartBeat, heartBeatUnit);
    mBuilder.setInitialPeersWithKeys(peerInfos);
    KademliaRouting kademlia1 = mBuilder.createPeer();

    BlockingQueue<NodeInfo> foundNeighbours = new LinkedBlockingQueue<>();
    NeighbourListener neighbourListener = new QueueingNeighbourListener(foundNeighbours);
    kademlia0.registerNeighbourListener(neighbourListener);

    kademlia0.start();
    kademlia1.start();

    NodeInfo neighbour = foundNeighbours.take();
    assertNotNull(neighbour);
    assertTrue(neighbour.getKey().equals(key1));
    kademlia1.stop();
    kademlia0.stop();
    kademlia0.unregisterNeighbourListener();
  }

  @Test
  public void shouldNotifyAboutNewNeighbour() throws KademliaException, InterruptedException {
    Key key0 = new Key(0);
    Key key1 = new Key(1);

    mBuilder.setKey(key0);
    KademliaRouting kademlia0 = mBuilder.createPeer();
    kademlia0.start();

    BlockingQueue<NodeInfo> foundNeighbours = new LinkedBlockingQueue<>();
    NeighbourListener neighbourListener = new QueueingNeighbourListener(foundNeighbours);
    kademlia0.registerNeighbourListener(neighbourListener);

    sendPing(key1, key0);

    NodeInfo neighbour = foundNeighbours.take();
    assertTrue(neighbour.getKey().equals(key1));
    kademlia0.stop();
    kademlia0.unregisterNeighbourListener();
  }

  /**
   * A kademlia peer with key 0 and bucket size 2 is sent messages from nodes with key 4 and 5 in
   * that order. Later it again receives message from node 4. After that it is contacted by a node
   * with key 6.
   *
   * The tested peer should replace last seen node 5 with node 6.
   */
  @Test
  public void shouldReplaceLeastRecentlyContactedNode()
      throws KademliaException, InterruptedException {
    final int bucketSize = 2;
    Key key0 = new Key(0);
    Key key4 = new Key(4);
    Key key5 = new Key(5);
    Key key6 = new Key(6);

    mBuilder.setKey(key0);
    mBuilder.setBucketSize(bucketSize);
    mBuilder.setMessageTimeout(10, TimeUnit.MILLISECONDS);
    KademliaRouting kademlia0 = mBuilder.createPeer();
    kademlia0.start();

    BlockingQueue<NodeInfo> foundNeighbours = new LinkedBlockingQueue<>();
    NeighbourListener neighbourListener = new QueueingNeighbourListener(foundNeighbours);
    kademlia0.registerNeighbourListener(neighbourListener);

    sendPing(key4, key0);
    sendPing(key5, key0);
    sendPing(key4, key0);

    NodeInfo neighbour = foundNeighbours.take();
    assertTrue(neighbour.getKey().equals(key4) || neighbour.getKey().equals(key5));
    neighbour = foundNeighbours.take();
    assertTrue(neighbour.getKey().equals(key4) || neighbour.getKey().equals(key5));

    sendPing(key6, key0);

    neighbour = foundNeighbours.take();
    assertTrue(neighbour.getKey().equals(key6));

    Collection<NodeInfo> currentNeighbours = kademlia0.getFlatRoutingTable();
    assertTrue(doesRoutingTableContainKey(currentNeighbours, key4));
    assertTrue(doesRoutingTableContainKey(currentNeighbours, key6));
    assertFalse(doesRoutingTableContainKey(currentNeighbours, key5));

    kademlia0.stop();
  }

  @Test
  public void shouldReplaceInactiveNodeWithNewNode()
      throws InterruptedException, KademliaException {
    final int bucketSize = 1;
    final long messageTimeout = 10;
    final TimeUnit messageTimeoutUnit = TimeUnit.MILLISECONDS;
    Key key0 = new Key(0);
    Key key2 = new Key(2);
    Key key3 = new Key(3);

    Collection<NodeInfo> peerInfos = new ArrayList<>();
    peerInfos.add(new NodeInfo(key0, mLocalAddress));

    mBuilder.setKey(key0);
    mBuilder.setBucketSize(bucketSize);
    mBuilder.setMessageTimeout(messageTimeout, messageTimeoutUnit);
    KademliaRouting kademlia0 = mBuilder.createPeer();

    StaticKademliaRouting kademlia2 = newStaticKademlia(2, bucketSize, peerInfos);
    StaticKademliaRouting kademlia3 = newStaticKademlia(3, bucketSize, peerInfos);
    kademlia0.start();
    kademlia2.start();

    BlockingQueue<NodeInfo> foundNeighbours = new LinkedBlockingQueue<>();
    NeighbourListener neighbourListener = new QueueingNeighbourListener(foundNeighbours);
    kademlia0.registerNeighbourListener(neighbourListener);

    kademlia2.sendPingToNode(key0);

    NodeInfo neighbour = foundNeighbours.take();
    assertEquals(neighbour.getKey(), key2);
    Collection<NodeInfo> routingTable = kademlia0.getFlatRoutingTable();
    assertTrue(doesRoutingTableContainKey(routingTable, key2));
    kademlia2.stop();

    mBuilder.setKey(key3);
    kademlia3.start();

    kademlia3.sendPingToNode(key0);

    neighbour = foundNeighbours.take();
    assertEquals(neighbour.getKey(), key3);

    routingTable = kademlia0.getFlatRoutingTable();
    assertFalse(doesRoutingTableContainKey(routingTable, key2));
    assertTrue(doesRoutingTableContainKey(routingTable, key3));
    kademlia3.stop();
    kademlia0.stop();
  }

  @Test
  public void shouldNotReplaceActiveNode()
      throws InterruptedException, KademliaException {
    final int bucketSize = 1;
    final long messageTimeout = 10;
    final TimeUnit messageTimeoutUnit = TimeUnit.MILLISECONDS;
    Key key0 = new Key(0);
    Key key2 = new Key(2);
    Key key3 = new Key(3);

    Collection<NodeInfo> peerInfos = new ArrayList<>();
    peerInfos.add(new NodeInfo(key0, mLocalAddress));

    mBuilder.setKey(key0);
    mBuilder.setBucketSize(bucketSize);
    mBuilder.setMessageTimeout(messageTimeout, messageTimeoutUnit);
    KademliaRouting kademlia0 = mBuilder.createPeer();

    StaticKademliaRouting kademlia2 = newStaticKademlia(2, bucketSize, peerInfos);
    StaticKademliaRouting kademlia3 = newStaticKademlia(3, bucketSize, peerInfos);
    kademlia0.start();
    kademlia2.start();

    BlockingQueue<NodeInfo> foundNeighbours = new LinkedBlockingQueue<>();
    NeighbourListener neighbourListener = new QueueingNeighbourListener(foundNeighbours);
    kademlia0.registerNeighbourListener(neighbourListener);

    kademlia2.sendPingToNode(key0);

    NodeInfo neighbour = foundNeighbours.take();
    assertEquals(neighbour.getKey(), key2);
    Collection<NodeInfo> routingTable = kademlia0.getFlatRoutingTable();
    assertTrue(doesRoutingTableContainKey(routingTable, key2));

    mBuilder.setKey(key3);
    kademlia3.start();

    kademlia3.sendPingToNode(key0);

    routingTable = kademlia0.getFlatRoutingTable();
    assertFalse(doesRoutingTableContainKey(routingTable, key3));
    assertTrue(doesRoutingTableContainKey(routingTable, key2));
    kademlia3.stop();
    kademlia2.stop();
    kademlia0.stop();
  }

  @Test
  public void shouldRespondWithPong() throws KademliaException, InterruptedException {
    final int bucketSize = 1;
    Key key0 = new Key(0);
    int key2 = 2;

    Collection<NodeInfo> peerInfos = new ArrayList<>();
    peerInfos.add(new NodeInfo(key0, mLocalAddress));

    mBuilder.setKey(key0);
    KademliaRouting kademlia0 = mBuilder.createPeer();

    StaticKademliaRouting kademlia2 = newStaticKademlia(key2, bucketSize, peerInfos);
    kademlia0.start();
    kademlia2.start();

    BlockingMessageListener blockingMessageListener = new BlockingMessageListener();
    kademlia2.setMessageListenerAdditionalActions(blockingMessageListener);
    kademlia2.sendPingToNode(key0);

    KademliaMessage message = blockingMessageListener.getMessage();
    assertNotNull(message);
    assertTrue(message instanceof PongMessage);

    kademlia2.stop();
    kademlia0.stop();
  }

  @Test
  public void shouldReturnLocalKey() {
    Key key = new Key(0);
    mBuilder.setKey(key);
    KademliaRouting kademlia = mBuilder.createPeer();
    assertEquals(key, kademlia.getLocalKey());
  }

  private static final class BlockingMessageListener implements MessageListener {
    private final BlockingQueue<KademliaMessage> mQueue;

    public BlockingMessageListener() {
      mQueue = new LinkedBlockingQueue<>();
    }

    public KademliaMessage getMessage() throws InterruptedException {
      return mQueue.take();
    }

    @Override
    public void receiveFindNodeMessage(FindNodeMessage msg) {
      try {
        mQueue.put(msg);
      } catch (InterruptedException e) {
        LOGGER.error("Unexpected interrupt.", e);
      }
    }

    @Override
    public void receiveFindNodeReplyMessage(FindNodeReplyMessage msg) {
      try {
        mQueue.put(msg);
      } catch (InterruptedException e) {
        LOGGER.error("Unexpected interrupt.", e);
      }
    }

    @Override
    public void receivePingMessage(PingMessage msg) {
      try {
        mQueue.put(msg);
      } catch (InterruptedException e) {
        LOGGER.error("Unexpected interrupt.", e);
      }
    }

    @Override
    public void receivePongMessage(PongMessage msg) {
      try {
        mQueue.put(msg);
      } catch (InterruptedException e) {
        LOGGER.error("Unexpected interrupt.", e);
      }
    }
  }

  private boolean doesRoutingTableContainKey(Collection<NodeInfo> infos, Key key) {
    for (NodeInfo info : infos) {
      if (info.getKey().equals(key)) {
        return true;
      }
    }
    return false;
  }

  private StaticKademliaRouting newStaticKademlia(int nr,
                                                  int bucketSize,
                                                  Collection<NodeInfo> knownPeers) {
    Key localKey = new Key(nr);
    InetSocketAddress socketAddress = new InetSocketAddress(nr);
    KademliaRoutingTable routingTable = new KademliaRoutingTable(localKey, bucketSize);
    routingTable.addAll(knownPeers);
    return new StaticKademliaRouting(new NodeInfo(localKey, socketAddress),
        new MessageSenderAdapter(mLocalMessaging.getByteSender(nr)),
        new MessageListeningServiceAdapter(mLocalMessaging.getByteListeningService(nr)),
        routingTable);
  }

  private void sendPing(Key from, Key to) {
    PingMessage pingMessage = new PingMessage(new NodeInfo(from, mLocalAddress),
        new NodeInfo(to, mLocalAddress), 1);
    mLocalSender.sendMessage(mLocalAddress, pingMessage);
  }
}

