package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;

public class CertificateStorage extends Observable {
  private final Map<Key, Map<Key, Certificate>> mCertificates;
  private final Map<Key, ZonedDateTime> mRevokedNodes;
  private final Map<Key, Object> mIssuerKeys;

  public CertificateStorage(Map<Key, Object> issuerKeys) {
    mCertificates = new HashMap<>();
    mRevokedNodes = new HashMap<>();
    mIssuerKeys = issuerKeys;
  }

  public synchronized void addCertificate(Certificate certificate) {
    removeExpiredCertificates();
    if (!isCertificateValid(certificate)) {
      return;
    } else if (mRevokedNodes.containsKey(certificate.mDHTKey)) {
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
    removeExpiredCertificates();
    for (Certificate certificate : certificates) {
      addCertificate(certificate);
    }
  }

  public synchronized void addCertificateRevocation(Certificate certificate) {
    removeExpiredCertificates();
    if (isCertificateValid(certificate)) {
      mRevokedNodes.put(certificate.mDHTKey, certificate.mExpirationDate);
      mCertificates.remove(certificate.mDHTKey);
    }
  }

  public synchronized Optional<Object> getPublicKey(Key key) {
    removeExpiredCertificates();
    Map<Key, Certificate> issuersMap = mCertificates.get(key);

    if (issuersMap == null) {
      return Optional.empty();
    }

    if (issuersMap.values().size() == 0) {
      return Optional.empty();
    }

    Certificate certificate = issuersMap.values().iterator().next();
    return Optional.of(certificate.mPublicKey);
  }

  public synchronized Collection<Certificate> getCertificates(Key key) {
    removeExpiredCertificates();
    Map<Key, Certificate> issuersMap = mCertificates.get(key);

    if (issuersMap == null) {
      return new ArrayList<>();
    } else {
      return issuersMap.values();
    }
  }

  public synchronized boolean isNodeCloseToExpiration(Key key) {
    removeExpiredCertificates();
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
    removeExpiredCertificates();
    return mCertificates.containsKey(key);
  }

  private boolean isCertificateCloseToExpiration(Certificate certificate) {
    return certificate.mExpirationDate.isBefore(ZonedDateTime.now().plusMinutes(10));
  }

  private boolean isCertificateValid(Certificate certificate) {
    if (certificate.mExpirationDate.isBefore(ZonedDateTime.now())) {
      return false;
    }

    if (!mIssuerKeys.containsKey(certificate.mIssuerKey)) {
      return false;
    }

    Object issuerPublicKey = mIssuerKeys.get(certificate.mIssuerKey);

    return certificate.verifySignature(issuerPublicKey);
  }

  private void removeExpiredCertificates() {
    Iterator<Map.Entry<Key, Map<Key, Certificate>>> issuerMapIter =
        mCertificates.entrySet().iterator();
    while (issuerMapIter.hasNext()) {
      Map<Key, Certificate> issuersMap = issuerMapIter.next().getValue();
      Iterator<Map.Entry<Key, Certificate>> issuerKeyIter = issuersMap.entrySet().iterator();
      while (issuerKeyIter.hasNext()) {
        Certificate certificate = issuerKeyIter.next().getValue();
        if (certificate.mExpirationDate.isBefore(ZonedDateTime.now())) {
          issuerKeyIter.remove();
        }
      }

      if (issuersMap.isEmpty()) {
        issuerMapIter.remove();
      }
    }

    Iterator<Key> revokedKeysIter = mRevokedNodes.keySet().iterator();
    while (revokedKeysIter.hasNext()) {
      ZonedDateTime expirationDateTime = mRevokedNodes.get(revokedKeysIter.next());
      if (expirationDateTime.isBefore(ZonedDateTime.now())) {
        revokedKeysIter.remove();
      }
    }
  }
}
