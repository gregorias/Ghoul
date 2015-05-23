package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.SignedCertificate;
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
                              Collection<SignedCertificate> certificates,
                              Collection<NodeInfo> foundNodes) {
    super(srcNodeInfo, destNodeInfo, id, certificateRequest, certificates);
    mFoundNodes = new ArrayList<>(foundNodes);
  }

  private FindNodeReplyMessage(@NotNull NodeInfo srcNodeInfo,
                               @NotNull NodeInfo destNodeInfo,
                               int id,
                               boolean certificateRequest,
                               Collection<SignedCertificate> certificates,
                               Collection<NodeInfo> foundNodes,
                               byte[] signature) {
    super(srcNodeInfo, destNodeInfo, id, certificateRequest, certificates, signature);
    mFoundNodes = new ArrayList<>(foundNodes);
  }

  public Collection<NodeInfo> getFoundNodes() {
    return new ArrayList<>(mFoundNodes);
  }

  public static FindNodeReplyMessage deserialize(ByteBuffer buffer)
      throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserializeContent(buffer);
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
    byte[] signature = KademliaMessage.deserializeSignature(buffer);
    return new FindNodeReplyMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        coreMsg.isCertificateRequest(),
        coreMsg.getCertificates(),
        foundNodes,
        signature);
  }

  @Override
  public String toString() {
    return String.format("FindNodeReplyMessage{src=%s, dest=%s, foundNodes.size()=%s,"
            + " certificateRequest=%s, certificates.size()=%s}",
        getSourceNodeInfo().getKey(),
        getDestinationNodeInfo().getKey(),
        mFoundNodes.size(),
        isCertificateRequest(),
        getCertificates().size());
  }

  @Override
  protected void serializeContent(ByteBuffer buffer) {
    super.serializeContent(buffer);
    byte size = (byte) mFoundNodes.size();
    buffer.put(size);
    for (NodeInfo nodeInfo : mFoundNodes) {
      nodeInfo.serialize(buffer);
    }
  }
}
