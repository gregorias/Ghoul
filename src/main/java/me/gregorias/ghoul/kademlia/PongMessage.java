package me.gregorias.ghoul.kademlia;

/**
 * PONG message.
 */
class PongMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  public PongMessage(NodeInfo srcNodeInfo, NodeInfo destNodeInfo, int id) {
    super(srcNodeInfo, destNodeInfo, id);
  }
}

