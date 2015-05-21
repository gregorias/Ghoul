package me.gregorias.ghoul.security;

import java.util.ArrayList;
import java.util.Collection;

public class PersonalCertificateManager {
  private Collection<SignedCertificate> mPersonalCertificates;

  public PersonalCertificateManager(Collection<SignedCertificate> personalCertificates) {
    mPersonalCertificates = new ArrayList<>(personalCertificates);
  }

  public Collection<SignedCertificate> getPersonalCertificates() {
    return new ArrayList<>(mPersonalCertificates);
  }
}
