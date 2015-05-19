package me.gregorias.ghoul.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class JoinDHTReplyMessage implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Collection<Certificate> mCertificates;

  public JoinDHTReplyMessage(Collection<Certificate> certificates) {
    mCertificates = new ArrayList<>(certificates);
  }

  public final Collection<Certificate> getCertificates() {
    return new ArrayList<>(mCertificates);
  }
}
