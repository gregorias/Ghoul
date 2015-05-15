package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Created by grzesiek on 24.04.15.
 */
public class GetKeyReplyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;
  private final Key mKey;
  private final byte[] mData;

  public GetKeyReplyMessage(@NotNull NodeInfo srcNodeInfo,
                            @NotNull NodeInfo destNodeInfo,
                            int id,
                            Key key,
                            Optional<byte[]> data) {
    super(srcNodeInfo, destNodeInfo, id);
    mKey = key;
    if (data.isPresent()) {
      mData = data.get();
    } else {
      mData = null;
    }
  }

  public Optional<byte[]> getData() {
    if (mData != null) {
      return Optional.of(mData);
    } else {
      return Optional.empty();
    }
  }

  public Key getKey() {
    return mKey;
  }

  public void serialize(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
    mKey.serialize(buffer);
    if (mData != null) {
      buffer.put((byte) 1);
      buffer.putInt(mData.length);
      buffer.put(mData);
    } else {
      buffer.put((byte) 0);
    }
  }

  public static GetKeyReplyMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);
    int id;
    try {
      id = buffer.getInt();
      Key key = Key.deserialize(buffer);
      byte hasData = buffer.get();
      Optional<byte[]> data;
      if (hasData == 0) {
        data = Optional.empty();
      } else {
        int dataLength = buffer.getInt();
        byte[] dataArr = new byte[dataLength];
        buffer.get(dataArr);
        data = Optional.of(dataArr);
      }
      return new GetKeyReplyMessage(srcNodeInfo, destNodeInfo, id, key, data);
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
  }

  @Override
  public String toString() {
    return String.format("GetKeyReplyMessage{src:%s, dest:%s, id:%d, key:%s, data:%d}",
        getSourceNodeInfo(),
        getDestinationNodeInfo(),
        getId(),
        mKey,
        mData == null ? null : mData.length);
  }
}

