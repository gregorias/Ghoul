package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.network.tcp.TCPMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SignedObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RegistrarMessageSenderImpl implements RegistrarMessageSender {
  private static final Logger LOGGER = LoggerFactory.getLogger(RegistrarMessageSenderImpl.class);


  private final Map<Key, RegistrarDescription> mRegistrarsMap;
  private final Map<Key, TCPMessageChannel> mChannels;

  public RegistrarMessageSenderImpl(Collection<RegistrarDescription> allRegistrars) {

    mRegistrarsMap = new HashMap<>();
    for (RegistrarDescription reg : allRegistrars) {
      mRegistrarsMap.put(reg.getKey(), reg);
    }

    mChannels = new HashMap<>();
  }

  @Override
  public synchronized void sendMessage(Key key, SignedObject msg) {
    RegistrarDescription description = mRegistrarsMap.get(key);
    TCPMessageChannel channel = mChannels.get(key);
    try {
      if (channel == null) {
        channel = TCPMessageChannel.create(description.getAddress());
        mChannels.put(key, channel);
      }

      try {
        channel.sendMessage(msg);
      } catch (IOException e) {
        channel = TCPMessageChannel.create(description.getAddress());
        mChannels.put(key, channel);
        channel.sendMessage(msg);
      }
    } catch (IOException e) {
      LOGGER.warn("sendMessage({}, {})", key, msg, e);
    }
  }
}
