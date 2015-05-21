package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;

public class StartKeyGenerationMessage extends RegistrarMessage {
  private static final long serialVersionUID = 1L;
  private final Collection<Key> mRegistrars;

  public StartKeyGenerationMessage(Key sender, PublicKey pubKey, Collection<Key> registrars) {
    super(sender, pubKey);
    mRegistrars = new ArrayList<>(registrars);
  }


  public Collection<Key> getRegistrars() {
    return new ArrayList<>(mRegistrars);
  }

  @Override
  public String toString() {
    return "StartKeyGenerationMessage{"
        + "sender=" + getSender()
        + ", mRegistrars=" + mRegistrars
        + '}';
  }
}
