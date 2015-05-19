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
import java.util.concurrent.LinkedBlockingDeque;
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

  private final Map<Integer, BlockingQueue<Optional<SignedObject>>> mInputQueues;
  private final Map<Integer, BlockingQueue<Certificate>> mCertificateQueues;
  private int mKGPID;

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
    mKGPID = 0;

    mLocalPort = localPort;
  }

  @Override
  public void run() {
    ServerSocket server;
    try {
      server = new ServerSocket(mLocalPort);
    } catch (IOException e) {
      LOGGER.error("run()", e);
      return;
    }

    while (true) {
      try {
        Socket socket = server.accept();
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
      try {
        Object msg = mMsgChannel.receiveMessage();

        if (!(msg instanceof SignedObject)) {
          LOGGER.warn("run(): Received unsigned object: {}.", msg);
        }

        SignedObject signedObject = (SignedObject) msg;
        Object content = signedObject.getObject();

        if (content instanceof RegistrarMessage) {
          handleRegistrar(mMsgChannel, signedObject, (RegistrarMessage) msg);
        } else if (content instanceof JoinDHTMessage) {
          JoinDHTMessage joinMsg = (JoinDHTMessage) msg;
          handleClient(mMsgChannel, signedObject, joinMsg);
        }
      } catch (ClassNotFoundException | IOException | GhoulProtocolException
          | InterruptedException | InvalidKeyException | SignatureException e) {
        LOGGER.warn("run()", e);
      } finally {
        try {
          mMsgChannel.close();
        } catch (IOException e) {
          LOGGER.warn("run()", e);
        }
      }
    }

    public void handleClient(
        TCPMessageChannel channel,
        SignedObject signedObject,
        JoinDHTMessage joinMsg)
        throws GhoulProtocolException, IOException, InterruptedException {
      int id = createKGPID();
      try {
        boolean isVerified = signedObject.verify(joinMsg.getPublicKey(),
            mCryptoTools.getSignature());
        if (!isVerified) {
          LOGGER.warn("handleClient(): Received JoinDHTMessage with wrong signature.");
          return;
        }

        Collection<RegistrarDescription> participants = chooseParticipants();
        broadcastStartProtocol(participants, joinMsg.getPublicKey(), id);
        KeyGenerationProtocol kgp = createKGP(
            participants.stream().map(RegistrarDescription::getKey).collect(Collectors.toList()),
            id);
        Optional<Key> keyOptional = kgp.call();
        if (!keyOptional.isPresent()) {
          LOGGER.warn("handleClient(): Could not generate key.");
          return;
        }

        Optional<Collection<Certificate>> certifcatesOptional = waitForCertificates(participants,
            id);
        if (!certifcatesOptional.isPresent()) {
          LOGGER.warn("handleClient(): Did not receive all certificates.");
          return;
        }

        Collection<Certificate> certificates = certifcatesOptional.get();
        Certificate myCertificate = createCertificate(joinMsg.getPublicKey(), keyOptional.get());
        certificates.add(myCertificate);

        JoinDHTReplyMessage reply = new JoinDHTReplyMessage(certificates);
        SignedObject signedReply =
            null;
        try {
          signedReply = new SignedObject(reply, mPrivateKey, mCryptoTools.getSignature());
        } catch (InvalidKeyException | SignatureException e) {
          throw new IllegalStateException("Unexpected signature exception.", e);
        }
        channel.sendMessage(signedReply);
      } catch (InvalidKeyException | SignatureException e) {
        throw new GhoulProtocolException(e);
      } finally {
        destroyKGPID(id);
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

      Key remoteSourceKey = registrarMessage.getSender();
      Optional<PublicKey> pubKeyOptional = getRegistrarPublicKey(remoteSourceKey);

      if (!pubKeyOptional.isPresent()) {
        LOGGER.warn("handleRegistrar(): Unknown registrar (key: {}) has sent a message."
            + " Ignoring it.", remoteSourceKey);
        continue;
      }

      PublicKey pubKey = pubKeyOptional.get();

      boolean isSignatureValid = signedObject.verify(pubKey, mCryptoTools.getSignature());
      if (!isSignatureValid) {
        continue;
      }

      if (registrarMessage instanceof StartKeyGenerationMessage) {
        StartKeyGenerationMessage startMsg = (StartKeyGenerationMessage) registrarMessage;

        Collection<Key> participants;
        if (!startMsg.getRegistrars().stream().anyMatch((reg) -> reg.equals(mKey))) {
          continue;
        } else {
          participants =  startMsg.getRegistrars().stream().filter((reg) -> !reg.equals(mKey))
              .collect(Collectors.toList());
        }

        KeyGenerationProtocol keyGenProtocol = createKGP(participants, registrarMessage.getId());

        final RegistrarMessage finalRegistrarMessage = registrarMessage;
        mExecutor.execute(() -> {
            Optional<Key> keyOptional = keyGenProtocol.call();
            if (keyOptional.isPresent()) {
              Certificate certificate = createCertificate(pubKey, keyOptional.get());
              CertificateMessage msg = new CertificateMessage(mKey, startMsg.getId(), certificate);
              SignedObject signedMsg = null;
              try {
                signedMsg = new SignedObject(msg, mPrivateKey,
                    mCryptoTools.getSignature());
              } catch (InvalidKeyException | SignatureException | IOException e) {
                LOGGER.warn("handleRegistrar()", e);
              }
              mRegistrarMessageSender.sendMessage(finalRegistrarMessage.getSender(), signedMsg);
            }
          });
      } else if (registrarMessage instanceof CommitmentMessage
          || registrarMessage instanceof ViewMessage) {
        int id = registrarMessage.getId();
        BlockingQueue<Optional<SignedObject>> inputQueue = mInputQueues.get(id);
        if (inputQueue != null) {
          inputQueue.put(Optional.of(signedObject));
        }
      } else if (registrarMessage instanceof CertificateMessage) {
        BlockingQueue<Certificate> queue = mCertificateQueues.get(registrarMessage.getId());
        if (queue != null) {
          queue.put(((CertificateMessage) registrarMessage).getCertificate());
        }
      }
    }
  }

  private void broadcastStartProtocol(
      Collection<RegistrarDescription> participants,
      PublicKey clientPublicKey,
      int id) throws InvalidKeyException, IOException, SignatureException {
    Collection<Key> keys = participants.stream()
        .map((reg) -> reg.getKey()).collect(Collectors.toList());
    keys.add(mKey);
    StartKeyGenerationMessage stmMsg = new StartKeyGenerationMessage(mKey,
        id,
        keys,
        clientPublicKey);
    SignedObject signedMsg = new SignedObject(stmMsg, mPrivateKey, mCryptoTools.getSignature());
    for (RegistrarDescription registrar : participants) {
      mRegistrarMessageSender.sendMessage(registrar.getKey(), signedMsg);
    }
  }

  private Collection<RegistrarDescription> chooseParticipants() {
    return mRegistrars.stream().filter((RegistrarDescription reg) -> reg.getKey() != mKey)
        .collect(Collectors.toList());
  }

  private Certificate createCertificate(PublicKey publicKey, Key key) {
    ZonedDateTime expiration = ZonedDateTime.now().plusDays(1);
    Certificate certificate = new Certificate(publicKey, key, mKey, expiration);
    certificate.signCertificate(mPrivateKey);
    return certificate;
  }

  private synchronized KeyGenerationProtocol createKGP(
      Collection<Key> participants,
      int id) {
    return new KeyGenerationProtocol(
        mKey,
        id,
        mPrivateKey,
        participants,
        mRegistrarMessageSender,
        mInputQueues.get(id),
        mExecutor,
        mCryptoTools,
        20,
        TimeUnit.SECONDS);
  }

  private synchronized int createKGPID() {
    int id = mKGPID++;
    BlockingQueue<Optional<SignedObject>> inputQueue = new LinkedBlockingDeque<>();
    mInputQueues.put(id, inputQueue);
    return id;
  }

  private synchronized void destroyKGPID(int id) {
    mInputQueues.remove(id);
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

  private Optional<Collection<Certificate>> waitForCertificates(
      Collection<RegistrarDescription> participants,
      int id) throws InterruptedException {
    BlockingQueue<Certificate> queue = mCertificateQueues.get(id);
    Collection<Certificate> certificates = new ArrayList<>();
    while (certificates.size() < participants.size()) {
      Certificate certificate = queue.poll(10, TimeUnit.SECONDS);
      if (certificate == null) {
        return Optional.empty();
      }
      certificates.add(certificate);
    }
    return Optional.of(certificates);
  }
}
