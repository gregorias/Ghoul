package me.gregorias.ghoul.interfaces.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import me.gregorias.ghoul.kademlia.KademliaRouting;
import me.gregorias.ghoul.kademlia.data.NodeInfo;
import org.glassfish.jersey.server.JSONP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

@Path("get_routing_table")
public final class KademliaGetRoutingTableResource {
  private static final Logger LOGGER = LoggerFactory
      .getLogger(KademliaGetRoutingTableResource.class);
  private final KademliaRouting mKademlia;

  public KademliaGetRoutingTableResource(KademliaRouting kademlia) {
    mKademlia = kademlia;
  }

  @GET
  @JSONP
  @Produces(MediaType.APPLICATION_JSON)
  public Response getRoutingTable() {
    LOGGER.info("getRoutingTable()");
    Collection<NodeInfo> nodeInfos;
    try {
      nodeInfos = mKademlia.getFlatRoutingTable();
    } catch (Exception e) {
      LOGGER.info("getRoutingTable() -> bad request");
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(String.format("Could not get routing table from kademlia: %s.", e)).build();
    }
    NodeInfoBean[] parsedNodeInfos = new NodeInfoBean[nodeInfos.size()];
    int idx = 0;
    for (NodeInfo nodeInfo : nodeInfos) {
      parsedNodeInfos[idx] = NodeInfoBean.fromNodeInfo(nodeInfo);
      ++idx;
    }
    NodeInfoCollectionBean bean = new NodeInfoCollectionBean();
    bean.setNodeInfo(parsedNodeInfos);
    LOGGER.info("getRoutingTable() -> ok");
    return Response.ok(bean).build();
  }
}