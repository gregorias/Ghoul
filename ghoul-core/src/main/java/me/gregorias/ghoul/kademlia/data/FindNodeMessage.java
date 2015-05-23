package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.SignedCertificate;
import me.gregorias.ghoul.utils.DeserializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * FIND_NODE message
 */
public final class FindNodeMessage extends KademliaMessage {
  private static final Logger LOGGER = LoggerFactory.getLogger(FindNodeMessage.class);
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
                         boolean certificateRequest,
                         Collection<SignedCertificate> certificates,
                         Key key) {
    super(srcNodeInfo, destNodeInfo, id, certificateRequest, certificates);
    mKey = key;
  }

  private FindNodeMessage(@NotNull NodeInfo srcNodeInfo,
                          @NotNull NodeInfo destNodeInfo,
                          int id,
                          boolean certificateRequest,
                          Collection<SignedCertificate> certificates,
                          Key key,
                          byte[] signature) {
    super(srcNodeInfo, destNodeInfo, id, certificateRequest, certificates, signature);
    mKey = key;
  }

  public Key getSearchedKey() {
    return mKey;
  }

  @Override
  public String toString() {
    return String.format("FindNodeMessage{src=%s, dest=%s, mKey=%s, certificateRequest=%s}",
        getSourceNodeInfo().getKey(),
        getDestinationNodeInfo().getKey(),
        mKey,
        isCertificateRequest());
  }

  public static FindNodeMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserializeContent(buffer);
    Key key = Key.deserialize(buffer);
    byte[] signature = KademliaMessage.deserializeSignature(buffer);

    FindNodeMessage msg = new FindNodeMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        coreMsg.isCertificateRequest(),
        coreMsg.getCertificates(),
        key,
        signature);
    return msg;
  }

  @Override
  protected void serializeContent(ByteBuffer buffer) {
    super.serializeContent(buffer);
    mKey.serialize(buffer);
  }
}
