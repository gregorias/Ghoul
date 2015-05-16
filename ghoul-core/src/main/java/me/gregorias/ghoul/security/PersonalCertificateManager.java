package me.gregorias.ghoul.security;

import java.util.ArrayList;
import java.util.Collection;

public class PersonalCertificateManager {
  private Collection<Certificate> mPersonalCertificates;

  public PersonalCertificateManager(Collection<Certificate> personalCertificates) {
    mPersonalCertificates = new ArrayList<>(personalCertificates);
  }

  public Collection<Certificate> getPersonalCertificates() {
    return new ArrayList<>(mPersonalCertificates);
  }
}
