package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.network.ByteSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Adapter from {@link me.gregorias.ghoul.network.ByteSender} to {@link MessageSender}.
 * It seralizes messages using {@link me.gregorias.ghoul.kademlia.MessageSerializer}.
 *
 * @author Grzegorz Milka
 */
public final class MessageSenderAdapter implements MessageSender {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageSenderAdapter.class);
  private ByteSender mByteSender;

  public MessageSenderAdapter(ByteSender byteSender) {
    mByteSender = byteSender;
  }

  @Override
  public void sendMessage(InetSocketAddress dest, KademliaMessage msg) {
    LOGGER.debug("sendMessage({}, {})", dest, msg);
    byte[] array = MessageSerializer.serializeMessage(msg);
    LOGGER.debug("sendMessage({}, {}): sending", dest, msg);
    mByteSender.sendMessage(dest, array);
  }
}
