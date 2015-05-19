package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.net.InetSocketAddress;
import java.security.PublicKey;

public class RegistrarDescription {
  private final PublicKey mPubKey;
  private final Key mKey;
  private final InetSocketAddress mAddress;

  public RegistrarDescription(
      PublicKey pubKey,
      Key key,
      InetSocketAddress address) {
    mPubKey = pubKey;
    mKey = key;
    mAddress = address;
  }

  public InetSocketAddress getAddress() {
    return mAddress;
  }

  public Key getKey() {
    return mKey;
  }

  public PublicKey getPublicKey() {
    return mPubKey;
  }
}
