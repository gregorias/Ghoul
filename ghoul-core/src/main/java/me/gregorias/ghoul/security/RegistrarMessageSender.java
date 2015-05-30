package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.security.SignedObject;
import java.util.concurrent.Future;

public interface RegistrarMessageSender {
  void sendMessage(Key key, SignedObject msg);
  Future<Boolean> sendMessageAsynchronously(Key key, SignedObject msg);
}
