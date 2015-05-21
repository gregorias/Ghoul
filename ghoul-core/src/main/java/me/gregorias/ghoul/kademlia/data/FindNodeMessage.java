package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.SignedCertificate;
import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * FIND_NODE message
 */
public final class FindNodeMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  private final Key mKey;

  public FindNodeMessage(@NotNull NodeInfo srcNodeInfo,
                         @NotNull NodeInfo destNodeInfo,
                         int id,
                         Key searchedKey) {
    super(srcNodeInfo, destNodeInfo, id);
    mKey = searchedKey;
  }

  public FindNodeMessage(@NotNull NodeInfo srcNodeInfo,
                         @NotNull NodeInfo destNodeInfo,
                         int id,
                         boolean certificateRequest, Collection<SignedCertificate> certificates,
                         Key key) {
    super(srcNodeInfo, destNodeInfo, id, certificateRequest, certificates);
    mKey = key;
  }

  public Key getSearchedKey() {
    return mKey;
  }

  @Override
  public String toString() {
    return String.format("FindNodeMessage{mKey:%s}", mKey);
  }

  public void serialize(ByteBuffer buffer) {
    super.serialize(buffer);
    mKey.serialize(buffer);
  }

  public static FindNodeMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserialize(buffer);
    Key key = Key.deserialize(buffer);

    FindNodeMessage msg = new FindNodeMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        coreMsg.isCertificateRequest(),
        coreMsg.getCertificates(),
        key);
    if (coreMsg.getSignature().isPresent()) {
      msg.setSignature(coreMsg.getSignature().get());
    }
    return msg;
  }
}
