package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.io.Serializable;
import java.time.ZonedDateTime;

public class Certificate implements Serializable {
  private static final long serialVersionUID = 1L;

  public Object mPublicKey;
  public Key mDHTKey;
  public ZonedDateTime mExpirationDate;
}
