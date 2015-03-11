package me.gregorias.ghoul.kademlia;

/**
 * PING message.
 */
class PingMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  public PingMessage(NodeInfo srcNodeInfo, NodeInfo destNodeInfo, int id) {
    super(srcNodeInfo, destNodeInfo, id);
  }
}

