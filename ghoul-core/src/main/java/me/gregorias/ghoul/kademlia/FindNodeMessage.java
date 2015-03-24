package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.utils.DeserializationException;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * FIND_NODE message.
 */
class FindNodeMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  private final Key mKey;

  public FindNodeMessage(NodeInfo srcNodeInfo, NodeInfo destNodeInfo, int id, Key searchedKey) {
    super(srcNodeInfo, destNodeInfo, id);
    mKey = searchedKey;
  }

  public Key getSearchedKey() {
    return mKey;
  }

  @Override
  public String toString() {
    return String.format("FindNodeMessage{mKey:%s}", mKey);
  }

  public void serialize(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
    mKey.serialize(buffer);
  }

  public static FindNodeMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);
    int id;
    try {
      id = buffer.getInt();
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    Key key = Key.deserialize(buffer);
    return new FindNodeMessage(srcNodeInfo, destNodeInfo, id, key);
  }
}
