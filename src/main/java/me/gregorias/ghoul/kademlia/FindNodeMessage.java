package me.gregorias.ghoul.kademlia;

/**
 * FIND_NODE message.
 */
class FindNodeMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  private final Key mKey;

  public FindNodeMessage(NodeInfo srcNodeInfo, NodeInfo destNodeInfo, int id, Key searchedKey) {
    super(srcNodeInfo, destNodeInfo, id);
    mKey = searchedKey;
  }

  public Key getSearchedKey() {
    return mKey;
  }
}

