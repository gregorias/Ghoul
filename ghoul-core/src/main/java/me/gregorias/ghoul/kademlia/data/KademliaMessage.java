package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.kademlia.data.NodeInfo;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Message sent between kademlia hosts.
 */
public abstract class KademliaMessage implements Serializable {
  private static final long serialVersionUID = 1L;

  private final NodeInfo mSrcNodeInfo;
  private final NodeInfo mDestNodeInfo;
  private final int mId;

  public KademliaMessage(@NotNull NodeInfo src, @NotNull NodeInfo dest, int id) {
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
