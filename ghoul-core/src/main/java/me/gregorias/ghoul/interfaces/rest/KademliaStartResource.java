package me.gregorias.ghoul.interfaces.rest;

import me.gregorias.ghoul.kademlia.KademliaRouting;
import me.gregorias.ghoul.kademlia.KademliaStore;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Start this Kademlia peer.
 *
 * @author Grzegorz Milka
 */
@Path("start")
public final class KademliaStartResource {
  private final KademliaRouting mKademlia;
  private final KademliaStore mStore;

  public KademliaStartResource(KademliaRouting kademlia, KademliaStore store) {
    mKademlia = kademlia;
    mStore = store;
  }

  @POST
  @Produces(MediaType.TEXT_PLAIN)
  public Response stop() {
    try {
      mKademlia.start();
      mStore.startUp();
    } catch (Exception e) {
      return Response.status(Response.Status.BAD_REQUEST).
          entity(String.format("Could not start up kademlia: %s.", e)).build();
    }
    return Response.ok().build();
  }
}
