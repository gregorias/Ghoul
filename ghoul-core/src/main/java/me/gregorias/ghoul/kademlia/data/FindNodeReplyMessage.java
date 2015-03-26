package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.utils.DeserializationException;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FindNodeReplyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  private final List<NodeInfo> mFoundNodes;

  public FindNodeReplyMessage(NodeInfo srcNodeInfo,
                              NodeInfo destNodeInfo,
                              int id,
                              Collection<NodeInfo> foundNodes) {
    super(srcNodeInfo, destNodeInfo, id);
    mFoundNodes = new ArrayList<>(foundNodes);
  }

  public Collection<NodeInfo> getFoundNodes() {
    return new ArrayList<>(mFoundNodes);
  }

  public void serialize(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
    byte size = (byte) mFoundNodes.size();
    buffer.put(size);
    for (NodeInfo nodeInfo : mFoundNodes) {
      nodeInfo.serialize(buffer);
    }
  }

  public static FindNodeReplyMessage deserialize(ByteBuffer buffer)
      throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);
    int id;
    byte size;
    try {
      id = buffer.getInt();
      size = buffer.get();
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    Collection<NodeInfo> foundNodes = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      foundNodes.add(NodeInfo.deserialize(buffer));
    }
    return new FindNodeReplyMessage(srcNodeInfo, destNodeInfo, id, foundNodes);
  }

  @Override
  public String toString() {
    return String.format("FindNodeReplyMessage{src:%s, foundNodes.size():%d}",
        getSourceNodeInfo().getKey(),
        mFoundNodes.size());
  }
}
