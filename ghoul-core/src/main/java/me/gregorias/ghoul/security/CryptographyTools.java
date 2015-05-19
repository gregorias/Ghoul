package me.gregorias.ghoul.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;

public class CryptographyTools {
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

  public MessageDigest getMessageDigest() {
    return mMessageDigest;
  }

  public SecureRandom getSecureRandom() {
    return mRandom;
  }

  public Signature getSignature() {
    return mSignature;
  }
}
