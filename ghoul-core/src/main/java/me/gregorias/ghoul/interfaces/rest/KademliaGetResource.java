package me.gregorias.ghoul.interfaces.rest;

import me.gregorias.ghoul.kademlia.KademliaStore;
import me.gregorias.ghoul.kademlia.data.KademliaException;
import me.gregorias.ghoul.kademlia.data.Key;
import org.glassfish.jersey.server.JSONP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collection;

@Path("get/{key}")
public class KademliaGetResource {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaGetResource.class);
  private final KademliaStore mStore;

  public KademliaGetResource(KademliaStore store) {
    mStore = store;
  }

  @GET
  @JSONP
  @Produces(MediaType.APPLICATION_JSON)
  public byte[][] get(@PathParam("key") String getKey) {
    LOGGER.info("get({})", getKey);
    Key key = new Key(Integer.parseInt(getKey));
    Collection<byte[]> foundData;
    try {
      foundData = mStore.getKey(key);
    } catch (IOException | KademliaException e) {
      LOGGER.error(String.format("get(%s)", key), e);
      return null;
    } catch (Exception e) {
      LOGGER.error(String.format("get(%s)", key), e);
      return null;
    }

    byte[][] foundDataBean = new byte[foundData.size()][];
    int idx = 0;
    for (byte[] data : foundData) {
      foundDataBean[idx] = data;
      ++idx;
    }

    LOGGER.info("get({}) -> foundDataBean.length: {}", key, foundDataBean.length);
    return foundDataBean;
  }
}
