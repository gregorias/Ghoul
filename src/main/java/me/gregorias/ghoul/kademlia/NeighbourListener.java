package me.gregorias.ghoul.kademlia;

/**
 * Listener for newly added neighbours.
 */
public interface NeighbourListener {
  void notifyNewNeighbour(NodeInfo neighbour);
}
