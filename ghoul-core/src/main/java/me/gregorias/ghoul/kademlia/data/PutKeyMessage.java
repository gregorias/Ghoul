package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Kademlia PUT_KEY message.
 */
public class PutKeyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;
  private final Key mKey;
  private final byte[] mData;

  public PutKeyMessage(@NotNull NodeInfo srcNodeInfo,
                       @NotNull NodeInfo destNodeInfo,
                       int id,
                       Key key,
                       byte[] data) {
    super(srcNodeInfo, destNodeInfo, id);
    mKey = key;
    mData = Arrays.copyOf(data, data.length);
  }

  public byte[] getData() {
    return Arrays.copyOf(mData, mData.length);
  }

  public Key getKey() {
    return mKey;
  }

  public void serialize(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
    mKey.serialize(buffer);
    buffer.putInt(mData.length);
    buffer.put(mData);
  }

  public static PutKeyMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);
    int id;
    try {
      id = buffer.getInt();
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    Key key = Key.deserialize(buffer);
    int dataLength = buffer.getInt();
    byte[] data = new byte[dataLength];
    try {
      buffer.get(data);
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    return new PutKeyMessage(srcNodeInfo, destNodeInfo, id, key, data);
  }

  @Override
  public String toString() {
    return String.format("PutKeyMessage{src:%s, dest:%s, id:%d, key:%s, data.length:%d}",
        getSourceNodeInfo(),
        getDestinationNodeInfo(),
        getId(),
        mKey,
        mData.length);
  }
}
