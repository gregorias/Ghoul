package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.kademlia.data.NodeInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Class which handles the storage part of the Kademlia protocol.
 */
public class KademliaStore {
  private final KademliaRouting mKademliaRouting;
  private final Store mStore;

  public KademliaStore(KademliaRouting kademliaRouting,
                       Store store) {
    mKademliaRouting = kademliaRouting;
    mStore = store;
  }

  public synchronized  Collection<byte[]> getKey(Key key) throws IOException, KademliaException {
    /* TODO */
    Collection<byte[]> foundKeys = new ArrayList<>();
    if (mStore.contains(key)) {
      foundKeys.add(mStore.get(key).get());
    }
    foundKeys.addAll(getRemoteData(key));
    return foundKeys;
  }

  public synchronized  void putKey(Key key, byte[] value) throws IOException, KademliaException, InterruptedException {
    Collection<NodeInfo> targetNodes = mKademliaRouting.findClosestNodes(key);
    putKeysOnTargetNodes(key, value, targetNodes);
  }

  private Collection<? extends byte[]> getRemoteData(Key key) {
    /* TODO */
    return null;
  }

  private void putKeysOnTargetNodes(Key key, byte[] value, Collection<NodeInfo> targetNodes) {
    /* TODO */
  }
}
