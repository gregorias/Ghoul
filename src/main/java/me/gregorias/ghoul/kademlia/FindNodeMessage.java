package me.gregorias.ghoul.kademlia;

/**
 * FIND_NODE message.
 */
class FindNodeMessage extends Message {
  private static final long serialVersionUID = 1L;

  private final Key mKey;

  public FindNodeMessage(NodeInfo srcNodeInfo, NodeInfo destNodeInfo, Key searchedKey) {
    super(srcNodeInfo, destNodeInfo);
    mKey = searchedKey;
  }

  public Key getSearchedKey() {
    return mKey;
  }
}

