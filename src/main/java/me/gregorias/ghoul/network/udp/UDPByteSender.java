package me.gregorias.ghoul.network.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import me.gregorias.ghoul.network.ByteSender;
import me.gregorias.ghoul.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link me.gregorias.ghoul.network.ByteSender} which sends messages in a
 * blocking way.
 *
 * @author Grzegorz Milka
 */
public class UDPByteSender implements ByteSender {
  private static final Logger LOGGER = LoggerFactory.getLogger(UDPByteSender.class);
  private final DatagramChannel mDatagramChannel;

  public UDPByteSender(DatagramChannel datagramChannel) {
    mDatagramChannel = datagramChannel;
  }

  /**
   * Send given message. This method blocks till the sending operation is
   * finished.
   *
   * @param dest Destination address
   * @param message Message to send
   */
  @Override
  public void sendMessage(InetSocketAddress dest, byte[] message) {
    LOGGER.trace("sendMessage({}, {})", dest, message.length);

    ByteBuffer buffer = Utils.arrayToByteBuffer(message);
    try {
      mDatagramChannel.send(buffer, dest);
    } catch (IOException e) {
      LOGGER.error("sendMessage(): IOException has been thrown when sending message.", e);
    }
  }
}
