package me.gregorias.ghoul.kademlia;

/**
 * PONG message.
 */
class PongMessage extends Message {
  private static final long serialVersionUID = 1L;

  public PongMessage(NodeInfo srcNodeInfo, NodeInfo destNodeInfo) {
    super(srcNodeInfo, destNodeInfo);
  }
}

