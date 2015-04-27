package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * GET_KEY message.
 */
public class GetKeyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;
  private final Key mKey;

  public GetKeyMessage(@NotNull NodeInfo srcNodeInfo,
                       @NotNull NodeInfo destNodeInfo,
                       int id,
                       Key key) {
    super(srcNodeInfo, destNodeInfo, id);
    mKey = key;
  }

  public Key getKey() {
    return mKey;
  }

  public void serialize(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
    mKey.serialize(buffer);
  }

  public static GetKeyMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);
    int id;
    try {
      id = buffer.getInt();
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    Key key = Key.deserialize(buffer);
    return new GetKeyMessage(srcNodeInfo, destNodeInfo, id, key);
  }

  @Override
  public String toString() {
    return String.format("GetKeyMessage{src:%s, dest:%s, id:%d, key:%s}",
        getSourceNodeInfo(),
        getDestinationNodeInfo(),
        getId(),
        mKey);
  }
}
