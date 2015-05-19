package me.gregorias.ghoul.security;

import me.gregorias.ghoul.network.tcp.TCPMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SignatureException;
import java.security.SignedObject;
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

  public Collection<Certificate> joinDHT() throws GhoulProtocolException, IOException {
    RegistrarDescription registrar = chooseRandomRegistrar();

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

  public boolean refreshCertificate(Certificate certificate) {
    // send refresh to given registrar.
    // receive refreshed certificate.
    // Check signature
    // TODO
    return true;
  }

  private RegistrarDescription chooseRandomRegistrar() {
    int idx = mCryptoTools.getSecureRandom().nextInt(mRegistrars.size());
    return mRegistrars.get(idx);
  }
}
