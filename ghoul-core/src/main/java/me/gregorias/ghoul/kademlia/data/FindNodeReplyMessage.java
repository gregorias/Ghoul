package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.Certificate;
import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reply to FIND_NODE message
 */
public final class FindNodeReplyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  private final List<NodeInfo> mFoundNodes;

  public FindNodeReplyMessage(@NotNull NodeInfo srcNodeInfo,
                              @NotNull NodeInfo destNodeInfo,
                              int id,
                              Collection<NodeInfo> foundNodes) {
    super(srcNodeInfo, destNodeInfo, id);
    mFoundNodes = new ArrayList<>(foundNodes);
  }

  public FindNodeReplyMessage(@NotNull NodeInfo srcNodeInfo,
                              @NotNull NodeInfo destNodeInfo,
                              int id,
                              boolean certificateRequest,
                              Collection<Certificate> certificates,
                              Collection<NodeInfo> foundNodes) {
    super(srcNodeInfo, destNodeInfo, id, certificateRequest, certificates);
    mFoundNodes = new ArrayList<>(foundNodes);
  }

  public Collection<NodeInfo> getFoundNodes() {
    return new ArrayList<>(mFoundNodes);
  }

  public void serialize(ByteBuffer buffer) {
    super.serialize(buffer);
    byte size = (byte) mFoundNodes.size();
    buffer.put(size);
    for (NodeInfo nodeInfo : mFoundNodes) {
      nodeInfo.serialize(buffer);
    }
  }

  public static FindNodeReplyMessage deserialize(ByteBuffer buffer)
      throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserialize(buffer);
    byte size;
    try {
      size = buffer.get();
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    Collection<NodeInfo> foundNodes = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      foundNodes.add(NodeInfo.deserialize(buffer));
    }
    FindNodeReplyMessage msg =  new FindNodeReplyMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        coreMsg.isCertificateRequest(),
        coreMsg.getCertificates(),
        foundNodes);
    if (coreMsg.getSignature().isPresent()) {
      msg.setSignature(coreMsg.getSignature().get());
    }
    return msg;
  }

  @Override
  public String toString() {
    return String.format("FindNodeReplyMessage{src:%s, foundNodes.size():%d}",
        getSourceNodeInfo().getKey(),
        mFoundNodes.size());
  }
}
