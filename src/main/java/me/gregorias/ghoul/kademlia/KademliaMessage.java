package me.gregorias.ghoul.kademlia;

import java.io.Serializable;

/**
 * Message sent between kademlia hosts.
 * It should information about source and destination, but both may be null if the situation
 * requires it.
 */
abstract class KademliaMessage implements Serializable {
  private static final long serialVersionUID = 1L;

  private final NodeInfo mSrcNodeInfo;
  private final NodeInfo mDestNodeInfo;
  private final int mId;

  KademliaMessage(NodeInfo src, NodeInfo dest, int id) {
    mSrcNodeInfo = src;
    mDestNodeInfo = dest;
    mId = id;
  }

  public NodeInfo getDestinationNodeInfo() {
    return mDestNodeInfo;
  }

  public NodeInfo getSourceNodeInfo() {
    return mSrcNodeInfo;
  }

  public int getId() {
    return mId;
  }
}
