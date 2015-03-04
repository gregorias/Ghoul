package me.gregorias.ghoul.network;

import java.net.InetSocketAddress;

/**
 * Observer for incoming messages.
 *
 * @author Grzegorz Milka
 */
public interface ByteListener {
  /**
   * Method to be called when a message is received.
   */
  void receiveMessage(InetSocketAddress sender, byte[] msg);
}