package me.gregorias.ghoul.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class JoinDHTReplyMessage implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Collection<SignedCertificate> mCertificates;

  public JoinDHTReplyMessage(Collection<SignedCertificate> certificates) {
    mCertificates = new ArrayList<>(certificates);
  }

  public final Collection<SignedCertificate> getCertificates() {
    return new ArrayList<>(mCertificates);
  }
}
