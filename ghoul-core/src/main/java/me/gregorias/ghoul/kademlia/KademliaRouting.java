package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.kademlia.data.NodeInfo;

import java.util.Collection;

/**
 * Peer in Kademlia's network.
 *
 * @author Grzegorz Milka
 */
public interface KademliaRouting {
  /**
   * findClosestNodes with size equal to bucket size parameter.
   *
   * @param key key to look up
   * @see KademliaRouting#findClosestNodes
   * @return Found nodes
   *
   * @throws InterruptedException method has been interrupted
   * @throws KademliaException kademlia could not finish the operation
   */
  Collection<NodeInfo> findClosestNodes(Key key) throws InterruptedException, KademliaException;

  /**
   * Find size number of nodes closest to given {@link Key}.
   *
   * @param key key to look up
   * @param size number of nodes to find
   * @return up to size found nodes
   *
   * @throws InterruptedException method has been interrupted
   * @throws KademliaException kademlia could not finish the operation
   */
  Collection<NodeInfo> findClosestNodes(Key key, int size) throws InterruptedException,
      KademliaException;

  /**
   * @return hosts present in the local routing table.
   */
  Collection<NodeInfo> getFlatRoutingTable();

  /**
   * @return Key representing this peer
   */
  Key getLocalKey();

  /**
   * @return is kademlia running.
   */
  boolean isRunning();

  /**
   * Registers neighbour listener which will receive notifications about newly added nodes.
   *
   * @param listener listener to register
   */
  void registerNeighbourListener(NeighbourListener listener);

  /**
   * Unregisters neighbour listener if any is present.
   */
  void unregisterNeighbourListener();

  /**
   * Connect and initialize this peer.
   *
   * @throws KademliaException kademlia could not finish the operation
   */
  void start() throws KademliaException;

  /**
   * Disconnects peer from network.
   *
   * @throws KademliaException kademlia could not finish the operation
   */
  void stop() throws KademliaException;
}

