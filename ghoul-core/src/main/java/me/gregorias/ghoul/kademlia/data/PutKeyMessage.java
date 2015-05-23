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

  public static PutKeyMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserializeContent(buffer);
    Key key = Key.deserialize(buffer);
    int dataLength = buffer.getInt();
    byte[] data = new byte[dataLength];
    try {
      buffer.get(data);
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    PutKeyMessage msg = new PutKeyMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        key,
        data);
    msg.setSignature(KademliaMessage.deserializeSignature(buffer));
    return msg;
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

  @Override
  protected void serializeContent(ByteBuffer buffer) {
    super.serializeContent(buffer);
    mKey.serialize(buffer);
    buffer.putInt(mData.length);
    buffer.put(mData);
  }
}
