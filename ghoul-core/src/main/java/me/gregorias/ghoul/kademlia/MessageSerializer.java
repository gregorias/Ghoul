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
  private static final int MAX_MESSAGE_SIZE = 1 << 12;

  public static byte[] serializeMessage(KademliaMessage msg) {
    ByteBuffer buffer = ByteBuffer.allocate(MAX_MESSAGE_SIZE);
    if (msg instanceof PingMessage) {
      buffer.put(PING_TAG);
      ((PingMessage) msg).serialize(buffer);
    } else if (msg instanceof PongMessage) {
      buffer.put(PONG_TAG);
      ((PongMessage) msg).serialize(buffer);
    } else if (msg instanceof FindNodeMessage) {
      buffer.put(FIND_NODE_TAG);
      ((FindNodeMessage) msg).serialize(buffer);
    } else if (msg instanceof FindNodeReplyMessage) {
      buffer.put(FIND_NODE_REPLY_TAG);
      ((FindNodeReplyMessage) msg).serialize(buffer);
    } else if (msg instanceof PutKeyMessage) {
      buffer.put(PUT_TAG);
      ((PutKeyMessage) msg).serialize(buffer);
    } else if (msg instanceof GetKeyMessage) {
      buffer.put(GET_TAG);
      ((GetKeyMessage) msg).serialize(buffer);
    } else if (msg instanceof GetKeyReplyMessage) {
      buffer.put(GET_REPLY_TAG);
      ((GetKeyReplyMessage) msg).serialize(buffer);
    }

    buffer.flip();
    return Utils.byteBufferToArray(buffer);
  }

  public static Optional<KademliaMessage> deserializeByteMessage(byte[] byteMsg) {
    ByteBuffer buffer = ByteBuffer.wrap(byteMsg);
    try {
      byte tag = buffer.get();
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
