package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.KademliaMessage;

import java.util.concurrent.BlockingQueue;

public class BlockingMessageListener implements MessageListener {
  private final BlockingQueue<KademliaMessage> mQueue;

  public BlockingMessageListener(BlockingQueue<KademliaMessage> queue) {
    mQueue = queue;
  }

  @Override
  public void receive(KademliaMessage msg) {
    try {
      mQueue.put(msg);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
