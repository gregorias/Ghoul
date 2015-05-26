package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.SignedCertificate;
import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.util.Collection;

public class GetDHTKeyReplyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;
  private final Key mKey;

  public GetDHTKeyReplyMessage(
      @NotNull NodeInfo srcNodeInfo,
      @NotNull NodeInfo destNodeInfo,
      int id,
      boolean shouldRequireCertificates,
      Collection<SignedCertificate> certificates,
      Key key) {
    super(srcNodeInfo, destNodeInfo, id, shouldRequireCertificates, certificates);
    mKey = key;
  }

  public Key getKey() {
    return mKey;
  }

  public static GetDHTKeyReplyMessage deserialize(ByteBuffer buffer)
      throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserializeContent(buffer);
    Key key = Key.deserialize(buffer);
    GetDHTKeyReplyMessage getKeyMessage = new GetDHTKeyReplyMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        coreMsg.isCertificateRequest(),
        coreMsg.getCertificates(),
        key);
    getKeyMessage.setSignature(KademliaMessage.deserializeSignature(buffer));
    return getKeyMessage;
  }

  @Override
  public String toString() {
    return String.format("GetDHTKeyReplyMessage{src:%s, dest:%s, id:%d, key:%s}",
        getSourceNodeInfo(),
        getDestinationNodeInfo(),
        getId(),
        mKey);
  }

  @Override
  protected void serializeContent(ByteBuffer buffer) {
    super.serializeContent(buffer);
    mKey.serialize(buffer);
  }
}
