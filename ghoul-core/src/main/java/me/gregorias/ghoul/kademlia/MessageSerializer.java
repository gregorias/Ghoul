package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.FindNodeMessage;
import me.gregorias.ghoul.kademlia.data.FindNodeReplyMessage;
import me.gregorias.ghoul.kademlia.data.GetKeyMessage;
import me.gregorias.ghoul.kademlia.data.GetKeyReplyMessage;
import me.gregorias.ghoul.kademlia.data.KademliaMessage;
import me.gregorias.ghoul.kademlia.data.PingMessage;
import me.gregorias.ghoul.kademlia.data.PongMessage;
import me.gregorias.ghoul.kademlia.data.PutKeyMessage;
import me.gregorias.ghoul.utils.DeserializationException;
import me.gregorias.ghoul.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Serialization utility for {@link KademliaMessage}.
 */
public class MessageSerializer {
  public static final byte PING_TAG = 0;
  public static final byte PONG_TAG = 1;
  public static final byte FIND_NODE_TAG = 2;
  public static final byte FIND_NODE_REPLY_TAG = 3;
  public static final byte PUT_TAG = 4;
  public static final byte GET_TAG = 5;
  public static final byte GET_REPLY_TAG = 6;
  public static final int MAX_MESSAGE_SIZE = 1 << 15;
  private static final Logger LOGGER = LoggerFactory.getLogger(MessageSerializer.class);

  public static byte[] serializeMessage(KademliaMessage msg) {
    ByteBuffer buffer = ByteBuffer.allocate(MAX_MESSAGE_SIZE);
    if (msg instanceof PingMessage) {
      buffer.put(PING_TAG);
      msg.serialize(buffer);
    } else if (msg instanceof PongMessage) {
      buffer.put(PONG_TAG);
      msg.serialize(buffer);
    } else if (msg instanceof FindNodeMessage) {
      buffer.put(FIND_NODE_TAG);
      msg.serialize(buffer);
    } else if (msg instanceof FindNodeReplyMessage) {
      buffer.put(FIND_NODE_REPLY_TAG);
      msg.serialize(buffer);
    } else if (msg instanceof PutKeyMessage) {
      buffer.put(PUT_TAG);
      msg.serialize(buffer);
    } else if (msg instanceof GetKeyMessage) {
      buffer.put(GET_TAG);
      msg.serialize(buffer);
    } else if (msg instanceof GetKeyReplyMessage) {
      buffer.put(GET_REPLY_TAG);
      msg.serialize(buffer);
    }

    buffer.flip();
    return Utils.byteBufferToArray(buffer);
  }

  public static Optional<KademliaMessage> deserializeByteMessage(byte[] byteMsg) {
    ByteBuffer buffer = ByteBuffer.wrap(byteMsg);
    try {
      byte tag = buffer.get();
      LOGGER.trace("deserializeByteMessage(): Deserializing message with tag: {}", tag);
      switch (tag) {
        case PING_TAG:
          return Optional.of(PingMessage.deserialize(buffer));
        case PONG_TAG:
          return Optional.of(PongMessage.deserialize(buffer));
        case FIND_NODE_TAG:
          return Optional.of(FindNodeMessage.deserialize(buffer));
        case FIND_NODE_REPLY_TAG:
          return Optional.of(FindNodeReplyMessage.deserialize(buffer));
        case PUT_TAG:
          return Optional.of(PutKeyMessage.deserialize(buffer));
        case GET_TAG:
          return Optional.of(GetKeyMessage.deserialize(buffer));
        case GET_REPLY_TAG:
          return Optional.of(GetKeyReplyMessage.deserialize(buffer));
        default:
          throw new DeserializationException("Unknown message tag.");
      }
    } catch (BufferUnderflowException | DeserializationException e) {
      return Optional.empty();
    }
  }
}
