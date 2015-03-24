package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.utils.DeserializationException;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * PONG message.
 */
class PongMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  public PongMessage(NodeInfo srcNodeInfo, NodeInfo destNodeInfo, int id) {
    super(srcNodeInfo, destNodeInfo, id);
  }

  public void serialize(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
  }

  public static PongMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);
    int id;
    try {
      id = buffer.getInt();
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    return new PongMessage(srcNodeInfo, destNodeInfo, id);
  }
}

