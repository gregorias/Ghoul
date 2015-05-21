package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.SignedCertificate;
import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * PING message.
 */
public final class PingMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  public PingMessage(@NotNull NodeInfo srcNodeInfo, @NotNull NodeInfo destNodeInfo, int id) {
    super(srcNodeInfo, destNodeInfo, id);
  }

  public PingMessage(@NotNull NodeInfo srcNodeInfo,
                     @NotNull NodeInfo destNodeInfo,
                     int id,
                     boolean certificateRequest,
                     Collection<SignedCertificate> certificates) {
    super(srcNodeInfo, destNodeInfo, id, certificateRequest, certificates);
  }

  public static PingMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserialize(buffer);
    PingMessage msg = new PingMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        coreMsg.isCertificateRequest(),
        coreMsg.getCertificates());
    if (coreMsg.getSignature().isPresent()) {
      msg.setSignature(coreMsg.getSignature().get());
    }
    return msg;
  }

  @Override
  public String toString() {
    return String.format("PingMessage{src:%s, dest:%s, id:%d}",
        getSourceNodeInfo(),
        getDestinationNodeInfo(),
        getId());
  }
}

