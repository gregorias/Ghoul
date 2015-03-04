package me.gregorias.ghoul.network;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Sender of messages.
 *
 * @author Grzegorz Milka
 */
public interface ByteSender {
  /**
   * Sends message
   *
   * @param destination Destination address
   * @param message
   */
  void sendMessage(InetSocketAddress destination, byte[] message) throws IOException;
}