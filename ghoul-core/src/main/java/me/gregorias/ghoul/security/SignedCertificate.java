package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.SignedObject;
import java.time.ZonedDateTime;
import java.util.Optional;

public class SignedCertificate implements Certificate, Serializable {
  private static final long serialVersionUID = 1L;

  private final SignedObject mSignedCertificate;
  private final Certificate mCertificate;

  private SignedCertificate(SignedObject signedCertificate) {
    mSignedCertificate = signedCertificate;
    mCertificate = unwrap(signedCertificate).get();
  }

  public static Optional<Key> getIssuerKey(SignedObject signedCertificate) {
    return unwrap(signedCertificate).map(Certificate::getIssuerKey);
  }

  public static SignedCertificate sign(
      Certificate certificate,
      PrivateKey privateKey,
      CryptographyTools tools) {
    try {
      return new SignedCertificate(tools.signObject(certificate, privateKey));
    } catch (InvalidKeyException | IOException | SignatureException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Optional<Certificate> unwrap(SignedObject signedCertificate) {
    Object potentialKey = null;
    try {
      potentialKey = signedCertificate.getObject();
    } catch (ClassNotFoundException | IOException e) {
      return Optional.empty();
    }

    if (potentialKey instanceof Certificate) {
      return Optional.of((Certificate) potentialKey);
    } else {
      return Optional.empty();
    }
  }

  public static boolean verify(
      SignedObject signedCertificate,
      PublicKey key,
      CryptographyTools tools) {
    try {
      boolean isVerified = tools.verifyObject(signedCertificate, key);
      if (!isVerified) {
        return false;
      }

      Optional<Certificate> certificate = unwrap(signedCertificate);
      return certificate.isPresent();
    } catch (InvalidKeyException | IOException | SignatureException e) {
      return false;
    }
  }

  public static Optional<SignedCertificate> verifyAndCreate(
      SignedObject signedCertificate,
      PublicKey key,
      CryptographyTools tools) {
    if (verify(signedCertificate, key, tools)) {
      return Optional.of(new SignedCertificate(signedCertificate));
    } else {
      return Optional.empty();
    }
  }

  public Certificate getBaseCertificate() {
    return mCertificate;
  }

  public SignedObject getSignedObject() {
    return mSignedCertificate;
  }

  @Override
  public Key getNodeDHTKey() {
    return mCertificate.getNodeDHTKey();
  }

  @Override
  public PublicKey getNodePublicKey() {
    return mCertificate.getNodePublicKey();
  }

  @Override
  public Key getIssuerKey() {
    return mCertificate.getIssuerKey();
  }

  @Override
  public ZonedDateTime getExpirationDateTime() {
    return mCertificate.getExpirationDateTime();
  }
}
