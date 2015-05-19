package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.security.SignedObject;

public interface RegistrarMessageSender {
  void sendMessage(Key key, SignedObject msg);
}
