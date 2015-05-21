package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.SignedCertificate;
import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * PONG message.
 */
public final class PongMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  public PongMessage(@NotNull NodeInfo srcNodeInfo, @NotNull NodeInfo destNodeInfo, int id) {
    super(srcNodeInfo, destNodeInfo, id);
  }

  public PongMessage(@NotNull NodeInfo srcNodeInfo,
                     @NotNull NodeInfo destNodeInfo,
                     int id,
                     boolean certificateRequest,
                     Collection<SignedCertificate> certificates) {
    super(srcNodeInfo, destNodeInfo, id, certificateRequest, certificates);
  }

  public static PongMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserialize(buffer);
    PongMessage msg = new PongMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        coreMsg.isCertificateRequest(),
        coreMsg.getCertificates());
    if (coreMsg.getSignature().isPresent()) {
      msg.setSignature(coreMsg.getSignature().get());
    }
    return msg;
  }
}

