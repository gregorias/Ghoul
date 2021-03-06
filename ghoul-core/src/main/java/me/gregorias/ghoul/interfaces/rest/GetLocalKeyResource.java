package me.gregorias.ghoul.interfaces.rest;

import me.gregorias.ghoul.kademlia.KademliaRouting;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("get_key")
public final class GetLocalKeyResource {
  private final KademliaRouting mKademlia;

  public GetLocalKeyResource(KademliaRouting kademlia) {
    mKademlia = kademlia;
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String getKey() {
    return mKademlia.getLocalKey().toString();
  }
}
