package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * PING message.
 */
public final class PingMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  public PingMessage(@NotNull NodeInfo srcNodeInfo, @NotNull NodeInfo destNodeInfo, int id) {
    super(srcNodeInfo, destNodeInfo, id);
  }

  public void serialize(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
  }

  public static PingMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);
    int id;
    try {
      id = buffer.getInt();
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    return new PingMessage(srcNodeInfo, destNodeInfo, id);
  }

  @Override
  public String toString() {
    return String.format("PingMessage{src:%s, dest:%s, id:%d}",
        getSourceNodeInfo(),
        getDestinationNodeInfo(),
        getId());
  }
}

