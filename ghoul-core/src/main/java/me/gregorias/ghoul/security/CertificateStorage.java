package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.security.SignedObject;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Optional;

public class CertificateStorage extends Observable {
  private static final Logger LOGGER = LoggerFactory.getLogger(CertificateStorage.class);
  private final Map<Key, Map<Key, Certificate>> mCertificates;
  private final Map<Key, ZonedDateTime> mRevokedNodes;
  private final Map<Key, PublicKey> mIssuerKeys;
  private final boolean mAllowSelfSigned;
  private final CryptographyTools mTools;

  public CertificateStorage(Map<Key, PublicKey> issuerKeys, CryptographyTools tools) {
    this(issuerKeys, tools, false);
  }

  public CertificateStorage(
      Map<Key, PublicKey> issuerKeys,
      CryptographyTools tools,
      boolean allowSelfSigned) {
    mCertificates = new HashMap<>();
    mRevokedNodes = new HashMap<>();
    mIssuerKeys = issuerKeys;
    mTools = tools;
    mAllowSelfSigned = allowSelfSigned;
  }

  public synchronized void addCertificate(SignedObject signedObject) {
    LOGGER.trace("addCertificate()");
    removeExpiredCertificates();
    if (!isCertificateValid(signedObject)) {
      LOGGER.debug("addCertificate(): An invalid certificate was tried to be added.");
      return;
    }

    Certificate certificate = SignedCertificate.unwrap(signedObject).get();

    if (mRevokedNodes.containsKey(certificate.getNodeDHTKey())) {
      return;
    }

    Map<Key, Certificate> issuersMap;
    if (mCertificates.containsKey(certificate.getNodeDHTKey())) {
      issuersMap = mCertificates.get(certificate.getNodeDHTKey());
    } else {
      issuersMap = new HashMap<>();
      mCertificates.put(certificate.getNodeDHTKey(), issuersMap);
    }

    if (issuersMap.containsKey(certificate.getIssuerKey())) {
      Certificate old = issuersMap.get(certificate.getIssuerKey());
      if (old.getExpirationDateTime().compareTo(certificate.getExpirationDateTime()) < 0) {
        issuersMap.put(certificate.getIssuerKey(), certificate);
      }
    } else {
      LOGGER.trace("addCertificate(): Adding the certificate {}.", certificate.getNodeDHTKey());
      issuersMap.put(certificate.getIssuerKey(), certificate);
    }
  }

  public void addCertificates(Collection<SignedObject> certificates) {
    removeExpiredCertificates();
    for (SignedObject certificate : certificates) {
      addCertificate(certificate);
    }
  }

  public synchronized void addCertificateRevocation(SignedObject signedObject) {
    removeExpiredCertificates();
    if (isCertificateValid(signedObject)) {
      Certificate certificate = SignedCertificate.unwrap(signedObject).get();
      mRevokedNodes.put(certificate.getNodeDHTKey(), certificate.getExpirationDateTime());
      mCertificates.remove(certificate.getNodeDHTKey());
    }
  }

  public synchronized Optional<PublicKey> getPublicKey(Key key) {
    removeExpiredCertificates();
    Map<Key, Certificate> issuersMap = mCertificates.get(key);

    if (issuersMap == null) {
      return Optional.empty();
    }

    if (issuersMap.values().size() == 0) {
      return Optional.empty();
    }

    Certificate certificate = issuersMap.values().iterator().next();
    return Optional.of(certificate.getNodePublicKey());
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
    return certificate.getExpirationDateTime().isBefore(ZonedDateTime.now().plusMinutes(10));
  }

  private boolean isCertificateValid(SignedObject certificate) {
    Certificate baseCertificate = SignedCertificate.unwrap(certificate).get();
    if (baseCertificate.getExpirationDateTime().isBefore(ZonedDateTime.now())) {
      LOGGER.trace("isCertificateValid(): Certificate's expiration date is due.");
      return false;
    }

    if (mAllowSelfSigned && baseCertificate.getIssuerKey().equals(
        baseCertificate.getNodeDHTKey())) {
      Optional<SignedCertificate> signedCertificate = SignedCertificate.verifyAndCreate(
          certificate, baseCertificate.getNodePublicKey(), mTools);
      if (signedCertificate.isPresent()) {
        return true;
      }
    }

    if (!mIssuerKeys.containsKey(baseCertificate.getIssuerKey())) {
      LOGGER.trace("isCertificateValid(): Certificate's issuer key is unknown.");
      return false;
    }

    PublicKey issuerPublicKey = mIssuerKeys.get(baseCertificate.getIssuerKey());

    return SignedCertificate.verify(certificate, issuerPublicKey, mTools);
  }

  private void removeExpiredCertificates() {
    Iterator<Map.Entry<Key, Map<Key, Certificate>>> issuerMapIter =
        mCertificates.entrySet().iterator();
    while (issuerMapIter.hasNext()) {
      Map<Key, Certificate> issuersMap = issuerMapIter.next().getValue();
      Iterator<Map.Entry<Key, Certificate>> issuerKeyIter = issuersMap.entrySet().iterator();
      while (issuerKeyIter.hasNext()) {
        Certificate certificate = issuerKeyIter.next().getValue();
        if (certificate.getExpirationDateTime().isBefore(ZonedDateTime.now())) {
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
