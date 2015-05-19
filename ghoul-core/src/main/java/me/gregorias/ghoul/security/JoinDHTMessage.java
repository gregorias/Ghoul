package me.gregorias.ghoul.security;

import java.io.Serializable;
import java.security.PublicKey;

public final class JoinDHTMessage implements Serializable {
  private static final long serialVersionUID = 1L;
  private final PublicKey mPubKey;

  public JoinDHTMessage(PublicKey pubKey) {
    mPubKey = pubKey;
  }

  public PublicKey getPublicKey() {
    return mPubKey;
  }
}
