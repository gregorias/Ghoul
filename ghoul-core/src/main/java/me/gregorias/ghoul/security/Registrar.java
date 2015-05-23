package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.network.tcp.TCPMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.SignedObject;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Registrar implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Registrar.class);
  private final Key mKey;
  private final PrivateKey mPrivateKey;
  //private final Map<Key, PublicKey> mRegistrarMap;
  private final RegistrarDescription mMyDescription;
  private final Collection<RegistrarDescription> mRegistrars;
  private final CryptographyTools mCryptoTools;

  private final RegistrarMessageSender mRegistrarMessageSender;
  private final ScheduledExecutorService mExecutor;

  private final Map<PublicKey, BlockingQueue<Optional<SignedObject>>> mInputQueues;
  private final Map<PublicKey, BlockingQueue<SignedCertificate>> mCertificateQueues;

  private final int mLocalPort;

  public Registrar(
      RegistrarDescription myself,
      PrivateKey privateKey,
      Collection<RegistrarDescription> registrars,
      RegistrarMessageSender sender,
      ScheduledExecutorService executor,
      CryptographyTools tools,
      int localPort) {
    mMyDescription = myself;
    mKey = myself.getKey();
    mPrivateKey = privateKey;
    mRegistrars = registrars;
    mRegistrarMessageSender = sender;
    mExecutor = executor;
    mCryptoTools = tools;

    mInputQueues = new HashMap<>();
    mCertificateQueues = new HashMap<>();

    mLocalPort = localPort;
  }

  @Override
  public void run() {
    LOGGER.debug("run()");

    ServerSocket server;
    try {
      server = new ServerSocket(mLocalPort);
      LOGGER.debug("run(): Listening at port: {}", mLocalPort);
    } catch (IOException e) {
      LOGGER.error("run()", e);
      return;
    }

    while (true) {
      try {
        Socket socket = server.accept();
        LOGGER.debug("run(): Accepted connection from: {}.", socket.getRemoteSocketAddress());
        TCPMessageChannel channel = TCPMessageChannel.create(socket);
        Worker worker = new Worker(channel);
        mExecutor.execute(worker);
      } catch (IOException e) {
        LOGGER.error("run()", e);
      }
    }
  }

  private class Worker implements Runnable {
    private final TCPMessageChannel mMsgChannel;

    public Worker(TCPMessageChannel msgChannel) {
      mMsgChannel = msgChannel;
    }

    @Override
    public void run() {
      LOGGER.trace("Worker.run()");
      try {
        Object msg = mMsgChannel.receiveMessage();

        if (!(msg instanceof SignedObject)) {
          LOGGER.warn("Worker.run(): Received unsigned object: {}.", msg);
        }

        SignedObject signedObject = (SignedObject) msg;
        Object content = signedObject.getObject();

        if (content instanceof RegistrarMessage) {
          LOGGER.trace("Worker.run(): Received registrar message: {}", content);
          handleRegistrar(mMsgChannel, signedObject, (RegistrarMessage) content);
        } else if (content instanceof JoinDHTMessage) {
          LOGGER.trace("Worker.run(): Received join message: {}", content);
          JoinDHTMessage joinMsg = (JoinDHTMessage) content;
          handleClient(mMsgChannel, signedObject, joinMsg);
        } else {
          LOGGER.warn("Worker.run(): Received unknown type of message: {}.", content);
        }
      } catch (ClassNotFoundException | IOException | GhoulProtocolException
          | InterruptedException | InvalidKeyException | SignatureException e) {
        LOGGER.warn("Worker.run()", e);
      } catch (Exception e) {
        LOGGER.warn("Worker.run()", e);
      } finally {
        try {
          LOGGER.trace("Worker.run(): Closing client channel.");
          mMsgChannel.close();
        } catch (IOException e) {
          LOGGER.warn("Worker.run()", e);
        }
      }
      LOGGER.trace("Worker.run() -> void");
    }

    public void handleClient(
        TCPMessageChannel channel,
        SignedObject signedObject,
        JoinDHTMessage joinMsg)
        throws GhoulProtocolException, IOException, InterruptedException {
      createKGPID(joinMsg.getPublicKey());
      try {
        boolean isVerified = mCryptoTools.verifyObject(signedObject, joinMsg.getPublicKey());
        if (!isVerified) {
          LOGGER.warn("handleClient(): Received JoinDHTMessage with wrong signature.");
          return;
        }

        Collection<RegistrarDescription> participants = chooseParticipants();
        broadcastStartProtocol(participants, joinMsg.getPublicKey());
        KeyGenerationProtocol kgp = createKGP(
            participants.stream().map(RegistrarDescription::getKey).collect(Collectors.toList()),
            mKey,
            joinMsg.getPublicKey());
        LOGGER.trace("handleClient(): Running key generation protocol.");
        Optional<Key> keyOptional = kgp.call();
        if (!keyOptional.isPresent()) {
          LOGGER.warn("handleClient(): Could not generate key.");
          return;
        }

        LOGGER.trace("handleClient(): Waiting for certificates from other registrars.");
        Optional<Collection<SignedCertificate>> certifcatesOptional = waitForCertificates(
            participants,
            joinMsg.getPublicKey());
        if (!certifcatesOptional.isPresent()) {
          LOGGER.warn("handleClient(): Did not receive all certificates.");
          return;
        }

        Collection<SignedCertificate> certificates = certifcatesOptional.get();
        SignedCertificate myCertificate = createCertificate(joinMsg.getPublicKey(),
            keyOptional.get());
        certificates.add(myCertificate);

        JoinDHTReplyMessage reply = new JoinDHTReplyMessage(certificates);
        SignedObject signedReply =
            null;
        try {
          signedReply = mCryptoTools.signObject(reply, mPrivateKey);
        } catch (InvalidKeyException | SignatureException e) {
          throw new IllegalStateException("Unexpected signature exception.", e);
        }
        channel.sendMessage(signedReply);
      } catch (InvalidKeyException | SignatureException e) {
        throw new GhoulProtocolException(e);
      } finally {
        destroyKGPID(joinMsg.getPublicKey());
      }
    }

  }

  public void handleRegistrar(
      TCPMessageChannel channel,
      SignedObject signedObject,
      RegistrarMessage registrarMessage)
      throws InterruptedException, IOException, ClassNotFoundException, SignatureException,
      InvalidKeyException {
    while (true) {
      if (registrarMessage == null) {
        Object msg = channel.receiveMessage();

        if (!(msg instanceof SignedObject)) {
          LOGGER.warn("handleRegistrar(): Received unsigned object: {}.", msg);
          continue;
        }

        signedObject = (SignedObject) msg;
        Object content = signedObject.getObject();

        if (!(content instanceof RegistrarMessage)) {
          LOGGER.warn("handleRegistrar(): Received unknown content : {}.", content);
        }

        registrarMessage = (RegistrarMessage) content;
      }

      LOGGER.trace("handleRegistrar(): Received message: {}.", registrarMessage);

      Key remoteSourceKey = registrarMessage.getSender();
      Optional<PublicKey> pubKeyOptional = getRegistrarPublicKey(remoteSourceKey);

      if (!pubKeyOptional.isPresent()) {
        LOGGER.warn("handleRegistrar(): Unknown registrar (key: {}) has sent a message."
            + " Ignoring it.", remoteSourceKey);
        registrarMessage = null;
        continue;
      }

      PublicKey pubKey = pubKeyOptional.get();

      boolean isSignatureValid = mCryptoTools.verifyObject(signedObject, pubKey);
      if (!isSignatureValid) {
        registrarMessage = null;
        continue;
      }

      if (registrarMessage instanceof StartKeyGenerationMessage) {
        StartKeyGenerationMessage startMsg = (StartKeyGenerationMessage) registrarMessage;

        Collection<Key> participants;
        if (!startMsg.getRegistrars().stream().anyMatch((reg) -> reg.equals(mKey))) {
          registrarMessage = null;
          continue;
        } else {
          participants =  startMsg.getRegistrars().stream().filter((reg) -> !reg.equals(mKey))
              .collect(Collectors.toList());
        }

        mInputQueues.put(startMsg.getClientPublicKey(), new LinkedBlockingQueue<>());

        KeyGenerationProtocol keyGenProtocol = createKGP(participants,
            registrarMessage.getSender(),
            startMsg.getClientPublicKey());

        final RegistrarMessage finalRegistrarMessage = registrarMessage;
        mExecutor.execute(() -> {
            Optional<Key> keyOptional = keyGenProtocol.call();
            if (keyOptional.isPresent()) {
              SignedCertificate certificate = createCertificate(startMsg.getClientPublicKey(),
                  keyOptional.get());
              CertificateMessage msg = new CertificateMessage(mKey,
                  startMsg.getClientPublicKey(),
                  certificate);
              SignedObject signedMsg = null;
              try {
                signedMsg = mCryptoTools.signObject(msg, mPrivateKey);
              } catch (InvalidKeyException | SignatureException | IOException e) {
                LOGGER.warn("handleRegistrar()", e);
              }
              mRegistrarMessageSender.sendMessage(finalRegistrarMessage.getSender(), signedMsg);
            }
            mInputQueues.remove(startMsg.getClientPublicKey());
          });
      } else if (registrarMessage instanceof CommitmentMessage
          || registrarMessage instanceof ViewMessage) {
        BlockingQueue<Optional<SignedObject>> inputQueue = mInputQueues
            .get(registrarMessage.getClientPublicKey());
        if (inputQueue != null) {
          inputQueue.put(Optional.of(signedObject));
        }
      } else if (registrarMessage instanceof CertificateMessage) {
        BlockingQueue<SignedCertificate> queue = mCertificateQueues
            .get(registrarMessage.getClientPublicKey());
        if (queue != null) {
          queue.put(((CertificateMessage) registrarMessage).getCertificate());
        }
      }
      registrarMessage = null;
    }
  }

  private void broadcastStartProtocol(
      Collection<RegistrarDescription> participants,
      PublicKey clientPublicKey) throws InvalidKeyException, IOException, SignatureException {
    Collection<Key> keys = participants.stream()
        .map((reg) -> reg.getKey()).collect(Collectors.toList());
    keys.add(mKey);
    StartKeyGenerationMessage stmMsg = new StartKeyGenerationMessage(mKey,
        clientPublicKey,
        keys);
    SignedObject signedMsg = mCryptoTools.signObject(stmMsg, mPrivateKey);
    for (RegistrarDescription registrar : participants) {
      LOGGER.trace("broadcastStartProtocol(): Sending StartProtocolMessage to: {}",
          registrar.getKey());
      mRegistrarMessageSender.sendMessage(registrar.getKey(), signedMsg);
    }
  }

  private Collection<RegistrarDescription> chooseParticipants() {
    return getOtherRegistrars();
  }

  private SignedCertificate createCertificate(PublicKey publicKey, Key key) {
    ZonedDateTime expiration = ZonedDateTime.now().plusDays(1);
    Certificate certificate = new CertificateImpl(publicKey, key, mKey, expiration);
    return SignedCertificate.sign(certificate, mPrivateKey, mCryptoTools);
  }

  private synchronized KeyGenerationProtocol createKGP(
      Collection<Key> participants,
      Key initiator,
      PublicKey clientPublicKey) {
    return new KeyGenerationProtocol(
        mKey,
        clientPublicKey,
        mPrivateKey,
        participants,
        mRegistrarMessageSender,
        mInputQueues.get(clientPublicKey),
        mExecutor,
        mCryptoTools,
        20,
        TimeUnit.SECONDS);
  }

  private synchronized void createKGPID(PublicKey publicKey) {
    BlockingQueue<Optional<SignedObject>> inputQueue = new LinkedBlockingQueue<>();
    mInputQueues.put(publicKey, inputQueue);
    mCertificateQueues.put(publicKey, new LinkedBlockingQueue<>());
  }

  private synchronized void destroyKGPID(PublicKey clientPublicKey) {
    mCertificateQueues.remove(clientPublicKey);
    mInputQueues.remove(clientPublicKey);
  }

  private Optional<PublicKey> getRegistrarPublicKey(Key key) {
    if (key.equals(mKey)) {
      return Optional.of(mMyDescription.getPublicKey());
    } else {
      Optional<RegistrarDescription> regOptional = mRegistrars.stream()
          .filter((reg) -> reg.getKey().equals(key)).findAny();
      Optional<PublicKey> keyOptional = regOptional.map(RegistrarDescription::getPublicKey);
      return keyOptional;
    }
  }

  private Collection<RegistrarDescription> getOtherRegistrars() {
    return mRegistrars.stream().filter(reg -> !reg.getKey()
        .equals(mKey)).collect(Collectors.toList());
  }

  private Optional<Collection<SignedCertificate>> waitForCertificates(
      Collection<RegistrarDescription> participants,
      PublicKey clientPublicKey) throws InterruptedException {
    BlockingQueue<SignedCertificate> queue = mCertificateQueues.get(clientPublicKey);
    Collection<SignedCertificate> certificates = new ArrayList<>();
    while (certificates.size() < participants.size()) {
      SignedCertificate certificate = queue.poll(10, TimeUnit.SECONDS);
      if (certificate == null) {
        return Optional.empty();
      }
      certificates.add(certificate);
    }
    return Optional.of(certificates);
  }
}
