package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.security.PublicKey;

public final class CertificateMessage extends RegistrarMessage {
  private static final long serialVersionUID = 1L;
  private final SignedCertificate mCertificate;

  public CertificateMessage(Key sender, PublicKey pubKey, SignedCertificate certificate) {
    super(sender, pubKey);
    mCertificate = certificate;
  }

  public SignedCertificate getCertificate() {
    return mCertificate;
  }
}
