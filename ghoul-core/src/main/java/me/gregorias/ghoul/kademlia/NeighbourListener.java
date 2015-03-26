package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.NodeInfo;

/**
 * Listener for newly added neighbours.
 */
public interface NeighbourListener {
  void notifyNewNeighbour(NodeInfo neighbour);
}
