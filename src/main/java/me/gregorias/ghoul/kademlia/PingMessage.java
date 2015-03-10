package me.gregorias.ghoul.kademlia;

/**
 * PING message.
 */
class PingMessage extends Message {
  private static final long serialVersionUID = 1L;

  public PingMessage(NodeInfo srcNodeInfo, NodeInfo destNodeInfo) {
    super(srcNodeInfo, destNodeInfo);
  }
}

