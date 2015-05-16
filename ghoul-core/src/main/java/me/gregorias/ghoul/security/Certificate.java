package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.io.Serializable;
import java.time.ZonedDateTime;

public class Certificate implements Serializable {
  private static final long serialVersionUID = 1L;

  public final Object mPublicKey;
  public final Key mDHTKey;
  public final Key mIssuerKey;
  public final ZonedDateTime mExpirationDate;
  public byte[] mSignature;

  public Certificate(Object publicKey,
                     Key dhtKey,
                     Key issuerKey,
                     ZonedDateTime expirationDate) {
    mPublicKey = publicKey;
    mDHTKey = dhtKey;
    mIssuerKey = issuerKey;
    mExpirationDate = expirationDate;
  }

  public void signCertificate(Object privateIssuerKey) {
  }

  public boolean verifySignature(Object publicIssuerKey) {
    return true;
  }
}
