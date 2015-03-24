package me.gregorias.ghoul.network;

import java.net.InetSocketAddress;

/**
 * Sender of messages.
 *
 * @author Grzegorz Milka
 */
public interface ByteSender {
  /**
   * Sends a message do given IP address. It keeps non-runtime errors are silent.
   *
   * @param destination Destination address
   * @param message Message to send
   */
  void sendMessage(InetSocketAddress destination, byte[] message);
}