package me.gregorias.ghoul.security;

import java.io.Serializable;

public final class RefreshCertificateReplyMessage implements Serializable {
  private static final long serialVersionUID = 1L;
  private final SignedCertificate mCertificate;

  public RefreshCertificateReplyMessage(SignedCertificate certificate) {
    mCertificate = certificate;
  }

  public SignedCertificate getCertificate() {
    return mCertificate;
  }
}

