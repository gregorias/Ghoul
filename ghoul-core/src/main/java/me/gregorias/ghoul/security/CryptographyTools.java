package me.gregorias.ghoul.security;

import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignedObject;

public class CryptographyTools {
  private static CryptographyTools DEFAULT;

  private final Signature mSignature;
  private final MessageDigest mMessageDigest;
  private final SecureRandom mRandom;

  public CryptographyTools(
      Signature signature,
      MessageDigest digest,
      SecureRandom random) {
    mSignature = signature;
    mMessageDigest = digest;
    mRandom = random;
  }

  public static synchronized CryptographyTools getDefault() {
    if (DEFAULT == null) {
      try {
        DEFAULT = new CryptographyTools(Signature.getInstance("Sha256WithDSA"),
            MessageDigest.getInstance("SHA-256"),
            new SecureRandom());
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }
    return DEFAULT;
  }

  public MessageDigest getMessageDigest() {
    return mMessageDigest;
  }

  public SecureRandom getSecureRandom() {
    return mRandom;
  }

  public Signature getSignature() {
    return mSignature;
  }

  public byte[] digestMessage(byte[] input) {
    synchronized (mMessageDigest) {
      mMessageDigest.reset();
      return mMessageDigest.digest(input);
    }
  }

  public byte[] signBytes(byte[] content, PrivateKey privKey)
      throws InvalidKeyException, IOException, SignatureException {
    synchronized (mSignature) {
      mSignature.initSign(privKey);
      mSignature.update(content);
      return mSignature.sign();
    }
  }

  public SignedObject signObject(Serializable object, PrivateKey privKey)
      throws InvalidKeyException, IOException, SignatureException {
    synchronized (mSignature) {
      return new SignedObject(object, privKey, mSignature);
    }
  }

  public boolean verifyBytes(byte[] content, byte[] signature, PublicKey pubKey)
      throws InvalidKeyException, IOException, SignatureException {
    synchronized (mSignature) {
      mSignature.initVerify(pubKey);
      mSignature.update(content);
      return mSignature.verify(signature);
    }
  }

  public boolean verifyObject(SignedObject object, PublicKey pubKey)
      throws InvalidKeyException, IOException, SignatureException {
    synchronized (mSignature) {
      return object.verify(pubKey, mSignature);
    }
  }
}
