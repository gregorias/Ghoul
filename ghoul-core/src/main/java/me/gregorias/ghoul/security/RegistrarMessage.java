package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.io.Serializable;
import java.security.PublicKey;

public class RegistrarMessage implements Serializable {
  private static final long serialVersionUID = 1L;
  private final Key mSender;
  private final PublicKey mClientPubKey;

  public RegistrarMessage(Key sender, PublicKey pubKey) {
    mSender = sender;
    mClientPubKey = pubKey;
  }

  public PublicKey getClientPublicKey() {
    return mClientPubKey;
  }

  public Key getSender() {
    return mSender;
  }
}
