package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.io.Serializable;
import java.security.PublicKey;
import java.time.ZonedDateTime;

public class CertificateImpl implements Certificate, Serializable {
  private static final long serialVersionUID = 1L;

  private final PublicKey mPublicKey;
  private final Key mDHTKey;
  private final Key mIssuerKey;
  private final ZonedDateTime mExpirationDate;

  public CertificateImpl(PublicKey publicKey,
                     Key dhtKey,
                     Key issuerKey,
                     ZonedDateTime expirationDate) {
    mPublicKey = publicKey;
    mDHTKey = dhtKey;
    mIssuerKey = issuerKey;
    mExpirationDate = expirationDate;
  }

  @Override
  public ZonedDateTime getExpirationDateTime() {
    return mExpirationDate;
  }

  @Override
  public Key getNodeDHTKey() {
    return mDHTKey;
  }

  @Override
  public PublicKey getNodePublicKey() {
    return mPublicKey;
  }

  @Override
  public Key getIssuerKey() {
    return mIssuerKey;
  }
}
