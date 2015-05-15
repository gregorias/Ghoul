package me.gregorias.ghoul.interfaces.rest;

import me.gregorias.ghoul.kademlia.KademliaStore;
import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.data.Key;
import org.glassfish.jersey.server.JSONP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

@Path("put/{key}")
public class KademliaPutResource {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaPutResource.class);
  private final KademliaStore mStore;

  public KademliaPutResource(KademliaStore store) {
    mStore = store;
  }

  @POST
  @JSONP
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  public void put(@PathParam("key") String putKey, byte[] value) {
    LOGGER.info("put({})", putKey);
    Key key = new Key(Integer.parseInt(putKey));
    try {
      mStore.putKey(key, value);
    } catch (IOException | KademliaException | InterruptedException e) {
      LOGGER.error(String.format("put(%s)", key), e);
      return;
    } catch (Exception e) {
      LOGGER.error(String.format("put(%s)", key), e);
      return;
    }
  }
}
