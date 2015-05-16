package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

public class CertificateStorage extends Observable {
  private final Map<Key, Map<Key, Certificate>> mCertificates;
  private final Collection<Certificate> mCertificateRevocations;
  private final ScheduledExecutorService mExecutor;

  public CertificateStorage(ScheduledExecutorService executor) {
    mCertificates = new HashMap<>();
    mCertificateRevocations = new ArrayList<>();
    mExecutor = executor;
    mExecutor.isShutdown();
  }

  public synchronized void addCertificate(Certificate certificate) {
    if (!isCertificateValid(certificate)) {
      return;
    }

    Map<Key, Certificate> issuersMap;
    if (mCertificates.containsKey(certificate.mDHTKey)) {
      issuersMap = mCertificates.get(certificate.mDHTKey);
    } else {
      issuersMap = new HashMap<>();
      mCertificates.put(certificate.mDHTKey, issuersMap);
    }

    if (issuersMap.containsKey(certificate.mIssuerKey)) {
      Certificate old = issuersMap.get(certificate.mIssuerKey);
      if (old.mExpirationDate.compareTo(certificate.mExpirationDate) < 0) {
        issuersMap.put(certificate.mIssuerKey, certificate);
      }
    } else {
      issuersMap.put(certificate.mIssuerKey, certificate);
    }

  }

  public void addCertificates(Collection<Certificate> certificates) {
    for (Certificate certificate : certificates) {
      addCertificate(certificate);
    }
  }

  public synchronized void addCertificateRevocation(Certificate certificate) {
    mCertificateRevocations.add(certificate);
  }

  public synchronized Optional<Object> getPublicKey(Key key) {
    return Optional.of(key);
  }

  public synchronized Collection<Certificate> getCertificates(Key key) {
    return new ArrayList<>();
  }

  public synchronized boolean isNodeCloseToExpiration(Key key) {
    if (mCertificates.containsKey(key)) {
      Map<Key, Certificate> issuersMap = mCertificates.get(key);
      for (Certificate certificate : issuersMap.values()) {
        if (isCertificateCloseToExpiration(certificate)) {
          return true;
        }
      }
    }

    return false;
  }

  public synchronized boolean isNodeValid(Key key) {
    return true;
  }

  private boolean isCertificateValid(Certificate certificate) {
    return true;
  }

  private boolean isCertificateCloseToExpiration(Certificate certificate) {
    return false;
  }
}
