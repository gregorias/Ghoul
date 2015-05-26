package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.SignedCertificate;
import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.util.Collection;

public class GetDHTKeyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  public GetDHTKeyMessage(
      @NotNull NodeInfo srcNodeInfo,
      @NotNull NodeInfo destNodeInfo,
      int id,
      boolean isCertificateRequest,
      Collection<SignedCertificate> certificates) {
    super(srcNodeInfo, destNodeInfo, id, isCertificateRequest, certificates);
  }

  public static GetDHTKeyMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserializeContent(buffer);
    GetDHTKeyMessage msg = new GetDHTKeyMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        coreMsg.isCertificateRequest(),
        coreMsg.getCertificates());
    msg.setSignature(KademliaMessage.deserializeSignature(buffer));
    return msg;
  }

  @Override
  public String toString() {
    return String.format("GetDHTKeyMessage{src:%s, dest:%s, id:%d}",
        getSourceNodeInfo(),
        getDestinationNodeInfo(),
        getId());
  }
}
