package me.gregorias.ghoul.kademlia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class FindNodeReplyMessage extends KademliaMessage {
  private static final long serialVersionUID = 1L;

  private final List<NodeInfo> mFoundNodes;

  public FindNodeReplyMessage(NodeInfo srcNodeInfo,
                              NodeInfo destNodeInfo,
                              int id,
                              Collection<NodeInfo> foundNodes) {
    super(srcNodeInfo, destNodeInfo, id);
    mFoundNodes = new ArrayList<>(foundNodes);
  }

  public Collection<NodeInfo> getFoundNodes() {
    return new ArrayList<>(mFoundNodes);
  }
}

