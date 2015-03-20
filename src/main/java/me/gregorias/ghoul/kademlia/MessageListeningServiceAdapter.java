package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.network.ByteListener;
import me.gregorias.ghoul.network.ByteListeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Optional;

//TODO
/**
 * Adapter from {@link me.gregorias.ghoul.network.ByteListeningService} to
 * {@link ListeningService} which deserializes messages using
 * {@link me.gregorias.ghoul.kademlia.MessageSerializer}.
 *
 * @author Grzegorz Milka
 */
class MessageListeningServiceAdapter implements ListeningService {
  private static final Logger LOGGER = LoggerFactory.getLogger(
      MessageListeningServiceAdapter.class);
  private final ByteListeningService mByteListeningService;
  private final ByteToMessageDeserializingListener mByteToMsgListener;
  private MessageListener mListener;

  public MessageListeningServiceAdapter(ByteListeningService byteListeningService) {
    mByteListeningService = byteListeningService;
    mByteToMsgListener = new ByteToMessageDeserializingListener();
  }

  @Override
  public synchronized void registerListener(MessageListener listener) {
    assert mListener == null;
    mListener = listener;
    mByteListeningService.registerListener(mByteToMsgListener);

  }

  @Override
  public synchronized void unregisterListener(MessageListener listener) {
    assert mListener != null && mListener.equals(listener);
    mByteListeningService.unregisterListener(mByteToMsgListener);
    mListener = null;
  }

  private class ByteToMessageDeserializingListener implements ByteListener {
    @Override
    public void receiveMessage(InetSocketAddress sender, byte[] byteMsg) {
      Optional<KademliaMessage> msg = MessageSerializer.deserializeByteMessage(byteMsg);
      if (msg.isPresent()) {
        synchronized (MessageListeningServiceAdapter.this) {
          mListener.receive(msg.get());
        }
      } else {
        LOGGER.trace("Received undeserializable message.");
      }
    }
  }
}

