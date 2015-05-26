package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.network.ByteListener;
import me.gregorias.ghoul.network.ByteListeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter from {@link me.gregorias.ghoul.network.ByteListeningService} to
 * {@link ListeningService} which deserializes messages using
 * {@link me.gregorias.ghoul.kademlia.MessageSerializer}.
 *
 * @author Grzegorz Milka
 */
public class MessageListeningServiceAdapter implements ListeningService {
  private static final Logger LOGGER = LoggerFactory.getLogger(
      MessageListeningServiceAdapter.class);
  private final ByteListeningService mByteListeningService;
  private final ByteToMessageDeserializingListener mByteToMsgListener;
  private Map<MessageListener, MessageMatcher> mListeners;

  public MessageListeningServiceAdapter(ByteListeningService byteListeningService) {
    mByteListeningService = byteListeningService;
    mByteToMsgListener = new ByteToMessageDeserializingListener();
    mListeners = new HashMap<>();
  }

  @Override
  public synchronized void registerListener(MessageMatcher matcher, MessageListener listener) {
    LOGGER.debug("registerListener({}, {})", matcher, listener);
    if (mListeners.size() == 0) {
      mByteListeningService.registerListener(mByteToMsgListener);
    }
    mListeners.put(listener, matcher);
  }

  @Override
  public synchronized void unregisterListener(MessageListener listener) {
    LOGGER.debug("unregisterListener({})", listener);
    mListeners.remove(listener);
    if (mListeners.size() == 0) {
      mByteListeningService.unregisterListener(mByteToMsgListener);
    }
  }

  private class ByteToMessageDeserializingListener implements ByteListener {
    @Override
    public void receiveMessage(InetSocketAddress sender, byte[] byteMsg) {
      Optional<KademliaMessage> msg = MessageSerializer.deserializeByteMessage(byteMsg);
      boolean hasMatched = false;
      if (msg.isPresent()) {
        synchronized (MessageListeningServiceAdapter.this) {
          for (Map.Entry<MessageListener, MessageMatcher> entry : mListeners.entrySet()) {
            if (entry.getValue().match(msg.get())) {
              entry.getKey().receive(msg.get());
              hasMatched = true;
              break;
            }
          }
        }
        if (!hasMatched) {
          LOGGER.trace("Received unmatched message: {}.", msg.get());

        }
      } else {
        LOGGER.trace("Received undeserializable message of length: {}.", byteMsg.length);
      }
    }
  }
}

