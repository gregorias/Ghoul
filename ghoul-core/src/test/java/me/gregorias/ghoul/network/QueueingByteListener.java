package me.gregorias.ghoul.network;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

/**
 * Byte listener which puts messages into a blocking queue.
 */
public class QueueingByteListener implements ByteListener {
  private final BlockingQueue<MessageWithSender> mQueue;

  public QueueingByteListener(BlockingQueue<MessageWithSender> queue) {
    mQueue = queue;
  }

  @Override
  public void receiveMessage(InetSocketAddress sender, byte[] msg) {
    try {
      mQueue.put(new MessageWithSender(sender, msg));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public static class MessageWithSender {
    private final byte[] mMsg;
    private final InetSocketAddress mSender;

    public MessageWithSender(InetSocketAddress sender, byte[] msg) {
      mSender = sender;
      mMsg = Arrays.copyOf(msg, msg.length);
    }

    public byte[] getMessage() {
      return Arrays.copyOf(mMsg, mMsg.length);
    }

    public InetSocketAddress getSender() {
      return mSender;
    }
  }
}

