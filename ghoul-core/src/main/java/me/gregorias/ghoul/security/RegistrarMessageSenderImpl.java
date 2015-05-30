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
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class RegistrarMessageSenderImpl implements RegistrarMessageSender {
  private static final Logger LOGGER = LoggerFactory.getLogger(RegistrarMessageSenderImpl.class);


  private final Map<Key, RegistrarDescription> mRegistrarsMap;
  private final Map<Key, TCPMessageChannel> mChannels;
  private final Executor mExecutor;

  public RegistrarMessageSenderImpl(Collection<RegistrarDescription> allRegistrars,
                                    Executor executor) {

    mRegistrarsMap = new HashMap<>();
    for (RegistrarDescription reg : allRegistrars) {
      mRegistrarsMap.put(reg.getKey(), reg);
    }

    mChannels = new HashMap<>();
    mExecutor = executor;
  }

  @Override
  public void sendMessage(Key destinationKey, SignedObject msg) {
    LOGGER.trace("sendMessage(destinationKey={}, msg={})", destinationKey, msg);
    RegistrarDescription description = mRegistrarsMap.get(destinationKey);
    TCPMessageChannel channel = mChannels.get(destinationKey);
    try {
      if (channel == null) {
        LOGGER.trace("sendMessage(destinationKey={}, msg={}): Creating a channel to {}.",
            destinationKey,
            msg,
            description.getAddress());
        channel = TCPMessageChannel.create(description.getAddress());
        mChannels.put(destinationKey, channel);
      }

      try {
        channel.sendMessage(msg);
        LOGGER.trace("sendMessage(destinationKey={}, msg={}) -> Message has been sent.",
            destinationKey,
            msg);
      } catch (IOException e) {
        LOGGER.trace("sendMessage(destinationKey={}, msg={}): Caught an exception."
                + " Creating the channel again.", e);
        channel = TCPMessageChannel.create(description.getAddress());
        mChannels.put(destinationKey, channel);
        channel.sendMessage(msg);
      }
    } catch (IOException e) {
      LOGGER.warn("sendMessage({}, {})", destinationKey, msg, e);
    }
  }

  public synchronized Future<Boolean> sendMessageAsynchronously(
      Key destinationKey,
      SignedObject msg) {
    FutureTask<Boolean> future = new FutureTask<Boolean>(() -> {
        sendMessage(destinationKey, msg);
        return true;
      });
    mExecutor.execute(future);
    return future;
  }
}
