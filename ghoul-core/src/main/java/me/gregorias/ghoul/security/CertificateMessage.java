package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

public final class CertificateMessage extends RegistrarMessage {
  private static final long serialVersionUID = 1L;
  private final Certificate mCertificate;

  public CertificateMessage(Key sender, int id, Certificate certificate) {
    super(sender, id);
    mCertificate = certificate;
  }

  public Certificate getCertificate() {
    return mCertificate;
  }
}
