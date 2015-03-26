package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.NodeInfo;

import java.util.concurrent.BlockingQueue;

/**
 * Neighbour listener which puts new neighbour events into a queue.
 */
public class QueueingNeighbourListener implements NeighbourListener {
  private final BlockingQueue<NodeInfo> mFoundNeighbours;

  public QueueingNeighbourListener(BlockingQueue<NodeInfo> foundNeighbours) {
    mFoundNeighbours = foundNeighbours;
  }

  @Override
  public void notifyNewNeighbour(NodeInfo neighbour) {
    try {
      mFoundNeighbours.put(neighbour);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
