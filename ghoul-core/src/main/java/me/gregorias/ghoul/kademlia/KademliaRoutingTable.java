package me.gregorias.ghoul.kademlia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A routing table used by kademlia. It keeps all nodes info separated into buckets of given size
 * and maintains order by least recent access.
 */
public class KademliaRoutingTable {
  private final List<List<NodeInfo>> mBuckets;
  private final Key mLocalKey;
  private final int mBucketSize;

  public KademliaRoutingTable(Key localKey, int bucketSize) {
    mLocalKey = localKey;
    mBucketSize = bucketSize;
    mBuckets = createBucketList();
  }

  /**
   *  Add node to the routing table as long as there is space left in the bucket.
   *
   *  @param newPeer peer to add
   */
  public synchronized void add(NodeInfo newPeer) {
    if (!newPeer.getKey().equals(mLocalKey)) {
      int distanceBit = mLocalKey.getDistanceBit(newPeer.getKey());
      if (mBuckets.get(distanceBit).size() < mBucketSize) {
        mBuckets.get(distanceBit).add(newPeer);
      }
    }
  }

  /**
   *  Add all nodes to the routing table as long as there is space left in the bucket.
   *  @param newPeers  peers to add
   */
  public synchronized void addAll(Collection<NodeInfo> newPeers) {
    newPeers.forEach(this::add);
  }

  public synchronized void clear() {
    mBuckets.forEach(List<NodeInfo>::clear);
  }

  public synchronized boolean contains(Key key) {
    int distanceBit = mLocalKey.getDistanceBit(key);

    List<NodeInfo> bucket = mBuckets.get(distanceBit);
    for (NodeInfo info : bucket) {
      if (info.getKey().equals(key)) {
        return true;
      }
    }
    return false;
  }

  public synchronized Optional<NodeInfo> get(Key key) {
    int distanceBit = mLocalKey.getDistanceBit(key);
    List<NodeInfo> bucket = mBuckets.get(distanceBit);
    return bucket.stream().filter((NodeInfo info) -> info.getKey().equals(key)).findAny();
  }

  /**
   * Get nodes closest to given key with default size of collection equal to bucket size.
   *
   * @param searchedKey key to search for
   * @return collection of nodes closest to given key
   */
  public Collection<NodeInfo> getClosestNodes(Key searchedKey) {
    return getClosestNodes(searchedKey, mBucketSize);
  }

  /**
   * Get nodes closest to given key.
   *
   * @param searchedKey key to search for
   * @param size maximal size of answer
   * @return collection of nodes closest to given key
   */
  public synchronized Collection<NodeInfo> getClosestNodes(final Key searchedKey, int size) {
    KeyComparator keyComparator = new KeyComparator(searchedKey);
    SortedMap<Key, NodeInfo> keyInfoMap = new TreeMap<>(keyComparator);

    flatten().stream().forEach((NodeInfo nodeInfo) -> keyInfoMap.put(nodeInfo.getKey(), nodeInfo));

    return keyInfoMap.values().stream().limit(size).collect(Collectors.toList());
  }

  public synchronized Optional<NodeInfo> getLeastRecentlyAccessedNode(int distanceBit) {
    List<NodeInfo> bucket = mBuckets.get(distanceBit);
    if (bucket.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(bucket.get(0));
    }
  }

  public synchronized Collection<NodeInfo> flatten() {
    Collection<NodeInfo> flatTable = new ArrayList<>();
    for (List<NodeInfo> list : mBuckets) {
      flatTable.addAll(list.stream().collect(Collectors.toList()));
    }
    return flatTable;
  }

  public synchronized boolean isBucketFull(int distanceBit) {
    return mBuckets.get(distanceBit).size() == mBucketSize;
  }

  public synchronized void replaceNode(NodeInfo oldNodeInfo, NodeInfo newNodeInfo) {
    int distanceBit = mLocalKey.getDistanceBit(oldNodeInfo.getKey());
    assert distanceBit == mLocalKey.getDistanceBit(newNodeInfo.getKey());
    List<NodeInfo> bucket = mBuckets.get(distanceBit);
    assert bucket.contains(oldNodeInfo);
    bucket.remove(oldNodeInfo);
    bucket.add(newNodeInfo);
  }

  /**
   * Signals this routing table that a node has been recently contacted and should be moved to the
   * end of the list of least recently contacted nodes and updated if necessary.
   *
   * @param nodeInfo NodeInfo to update
   */
  public synchronized void updateNode(NodeInfo nodeInfo) {
    int distanceBit = mLocalKey.getDistanceBit(nodeInfo.getKey());
    List<NodeInfo> bucket = mBuckets.get(distanceBit);
    for (int i = 0; i < bucket.size(); ++i) {
      if (bucket.get(i).getKey().equals(nodeInfo.getKey())) {
        bucket.remove(i);
        bucket.add(nodeInfo);
        break;
      }
    }
  }

  private List<List<NodeInfo>> createBucketList() {
    List<List<NodeInfo>> buckets = new ArrayList<>();

    for (int i = 0; i < Key.KEY_LENGTH; ++i) {
      buckets.add(new ArrayList<>());
    }

    return buckets;
  }
}
