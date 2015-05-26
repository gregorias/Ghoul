package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.network.tcp.TCPMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SignatureException;
import java.security.SignedObject;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RegistrarClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(RegistrarClient.class);

  private final List<RegistrarDescription> mRegistrars;
  private final KeyPair mKeyPair;
  private final CryptographyTools mCryptoTools;

  public RegistrarClient(
      Collection<RegistrarDescription> registrars,
      KeyPair keyPair,
      CryptographyTools tools) {
    mRegistrars = new ArrayList<>(registrars);
    mKeyPair = keyPair;
    mCryptoTools = tools;
  }

  public Collection<SignedCertificate> joinDHT() throws GhoulProtocolException, IOException {
    LOGGER.trace("joinDHT()");
    RegistrarDescription registrar = chooseRandomRegistrar();

    LOGGER.trace("joinDHT(): creating TCP channel to: {}", registrar.getAddress());
    TCPMessageChannel channel = TCPMessageChannel.create(registrar.getAddress());
    try {
      JoinDHTMessage joinMsg = new JoinDHTMessage(mKeyPair.getPublic());

      SignedObject signedJoinMsg;
      try {
        signedJoinMsg = new SignedObject(joinMsg,
            mKeyPair.getPrivate(),
            mCryptoTools.getSignature());
      } catch (InvalidKeyException | SignatureException e) {
        LOGGER.error("joinDHT()", e);
        throw new IllegalStateException(e);
      }

      LOGGER.trace("joinDHT(): Sending join message to registrar.");
      channel.sendMessage(signedJoinMsg);

      try {
        Optional<Object> response = channel.receiveMessage(1, TimeUnit.MINUTES);
        if (!response.isPresent()) {
          LOGGER.warn("joinDHT(): DHT join operation has timed out.", response);
          throw new GhoulProtocolException("DHT join operation has timed out.");

        }

        if (!(response.get() instanceof SignedObject)) {
          LOGGER.warn("joinDHT(): Received object of wrong class: {}", response);
          throw new GhoulProtocolException();
        }
        SignedObject signedObject = (SignedObject) response.get();
        boolean isVerified = signedObject.verify(registrar.getPublicKey(),
            mCryptoTools.getSignature());
        if (!isVerified) {
          throw new GhoulProtocolException("Received response with invalid signature.");
        }

        Object content = signedObject.getObject();
        if (!(content instanceof JoinDHTReplyMessage)) {
          throw new GhoulProtocolException("Received signed content is not a reply.");
        }

        JoinDHTReplyMessage reply = (JoinDHTReplyMessage) content;
        LOGGER.trace("joinDHT() -> returning received certificates. Number of certificates: {}.",
            reply.getCertificates().size());
        return reply.getCertificates();
      } catch (ClassNotFoundException e) {
        throw new GhoulProtocolException("Received response with invalid class.", e);
      } catch (SignatureException | InvalidKeyException e) {
        throw new GhoulProtocolException("Received response with invalid signature.", e);
      }
    } finally {
      channel.close();
    }
  }

  public SignedCertificate refreshCertificate(SignedCertificate certificate)
      throws GhoulProtocolException, IOException {
    LOGGER.debug("refreshCertificate({}): Starting protocol for refreshing the certificate.",
        certificate);
    RegistrarDescription registrar = getRegistrar(certificate.getIssuerKey());

    LOGGER.trace("refreshCertificate(): creating TCP channel to: {}", registrar.getAddress());
    TCPMessageChannel channel = TCPMessageChannel.create(registrar.getAddress());
    try {
      RefreshCertificateMessage refreshMessage = new RefreshCertificateMessage(certificate);

      SignedObject signedRefreshMessage;
      try {
        signedRefreshMessage = new SignedObject(refreshMessage,
            mKeyPair.getPrivate(),
            mCryptoTools.getSignature());
      } catch (InvalidKeyException | SignatureException e) {
        throw new IllegalStateException(e);
      }

      LOGGER.trace("refreshCertificate(): Sending refresh message to registrar.");
      channel.sendMessage(signedRefreshMessage);

      try {
        Optional<Object> response = channel.receiveMessage(1, TimeUnit.MINUTES);
        if (!response.isPresent()) {
          throw new GhoulProtocolException("Refresh operation has timed out.");
        }

        if (!(response.get() instanceof SignedObject)) {
          throw new GhoulProtocolException("Received unsigned object.");
        }

        SignedObject signedObject = (SignedObject) response.get();
        boolean isVerified = signedObject.verify(registrar.getPublicKey(),
            mCryptoTools.getSignature());
        if (!isVerified) {
          throw new GhoulProtocolException("Received response with invalid signature.");
        }

        Object content = signedObject.getObject();

        if (!(content instanceof RefreshCertificateReplyMessage)) {
          throw new GhoulProtocolException("Received signed content is not a reply.");
        }

        RefreshCertificateReplyMessage reply = (RefreshCertificateReplyMessage) content;
        SignedCertificate newCertificate = reply.getCertificate();
        if (!newCertificate.getIssuerKey().equals(certificate.getIssuerKey())
            || !newCertificate.getNodeDHTKey().equals(certificate.getNodeDHTKey())
            || newCertificate.getExpirationDateTime().isBefore(ZonedDateTime.now())
            || !newCertificate.getNodePublicKey().equals(certificate.getNodePublicKey())) {
          throw new GhoulProtocolException("Received invalid certificate");
        }

        return newCertificate;
      } catch (ClassNotFoundException e) {
        throw new GhoulProtocolException("Received response with invalid class.", e);
      } catch (SignatureException | InvalidKeyException e) {
        throw new GhoulProtocolException("Received response with invalid signature.", e);
      }
    } finally {
      channel.close();
    }
  }

  private RegistrarDescription chooseRandomRegistrar() {
    int idx = mCryptoTools.getSecureRandom().nextInt(mRegistrars.size());
    return mRegistrars.get(idx);
  }

  private RegistrarDescription getRegistrar(Key issuerKey) {
    Optional<RegistrarDescription> registrarOptional = mRegistrars.stream()
        .filter(reg -> reg.getKey().equals(issuerKey)).findAny();
    if (registrarOptional.isPresent()) {
      return registrarOptional.get();
    } else {
      throw new IllegalArgumentException("Received key to unknown registrar.");
    }
  }
}
