package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.utils.DeserializationException;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;

/**
 * GET_KEY message.
 */
public class GetKeyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;
  private final Key mKey;

  public GetKeyMessage(@NotNull NodeInfo srcNodeInfo,
                       @NotNull NodeInfo destNodeInfo,
                       int id,
                       Key key) {
    super(srcNodeInfo, destNodeInfo, id);
    mKey = key;
  }

  public Key getKey() {
    return mKey;
  }

  public static GetKeyMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    KademliaMessage coreMsg = KademliaMessage.deserializeContent(buffer);
    Key key = Key.deserialize(buffer);
    GetKeyMessage getKeyMessage = new GetKeyMessage(coreMsg.getSourceNodeInfo(),
        coreMsg.getDestinationNodeInfo(),
        coreMsg.getId(),
        key);
    getKeyMessage.setSignature(KademliaMessage.deserializeSignature(buffer));
    return getKeyMessage;
  }

  @Override
  public String toString() {
    return String.format("GetKeyMessage{src:%s, dest:%s, id:%d, key:%s}",
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
