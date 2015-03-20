package me.gregorias.ghoul.interfaces.rest;

import java.util.Arrays;

public final class NodeInfoCollectionBean {
  private NodeInfoBean[] mNodeInfos = new NodeInfoBean[0];

  public NodeInfoBean[] getNodeInfo() {
    return Arrays.copyOf(mNodeInfos, mNodeInfos.length);
  }

  public void setNodeInfo(NodeInfoBean[] nodeInfos) {
    mNodeInfos = Arrays.copyOf(nodeInfos, nodeInfos.length);
  }
}
