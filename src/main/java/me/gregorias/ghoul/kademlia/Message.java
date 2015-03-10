package me.gregorias.ghoul.kademlia;

import java.io.Serializable;

/**
 * Message sent between kademlia hosts.
 * It should information about source and destination, but both may be null if the situation
 * requires it.
 */
abstract class Message implements Serializable {
  private static final long serialVersionUID = 1L;

  private final NodeInfo mSrcNodeInfo;
  private final NodeInfo mDestNodeInfo;

  Message(NodeInfo src, NodeInfo dest) {
    mSrcNodeInfo = src;
    mDestNodeInfo = dest;
  }

  public NodeInfo getDestinationNodeInfo() {
    return mDestNodeInfo;
  }

  public NodeInfo getSourceNodeInfo() {
    return mSrcNodeInfo;
  }
}
