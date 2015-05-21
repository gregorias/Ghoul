package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.io.Serializable;
import java.security.PublicKey;
import java.time.ZonedDateTime;

public interface Certificate extends Serializable {
  Key getNodeDHTKey();
  PublicKey getNodePublicKey();
  Key getIssuerKey();
  ZonedDateTime getExpirationDateTime();
}