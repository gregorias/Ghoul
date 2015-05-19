package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignedObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class KeyGenerationProtocol implements Callable<Optional<Key>> {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeyGenerationProtocol.class);
  private final Key mMyRegistrarKey;
  private final int mID;
  private final PrivateKey mPrivateKey;
  private final Collection<Key> mRegistrars;
  private final RegistrarMessageSender mMessageSender;
  private final BlockingQueue<Optional<SignedObject>> mMessageQueue;
  private final SecureRandom mRandom;
  private final ScheduledExecutorService mExecutor;
  private final MessageDigest mDigest;
  private final Signature mSigningEngine;

  private final long mStageTimeout;
  private final TimeUnit mStageTimeoutUnit;

  private final Map<Key, SignedObject> mReceivedCommitmentMessages;
  private final Map<Key, byte[]> mReceivedCommitments;
  private final Map<Key, ViewMessage> mReceivedViewMessages;
  private final Collection<Key> mReceivedShareKeys;

  private SignedObject mMyCommitmentMessage;


  private boolean mIsStage0Timeout = false;
  private boolean mIsStage1Timeout = false;

  private Key mMyShareKey;
  private CommitmentPair mCommitmentPair;


  public KeyGenerationProtocol(
      Key myRegistrarKey,
      int id,
      PrivateKey privateKey,
      Collection<Key> registrars,
      RegistrarMessageSender sender,
      BlockingQueue<Optional<SignedObject>> messageQueue,
      ScheduledExecutorService executor,
      CryptographyTools cryptoTools,
      long stageTimeout,
      TimeUnit stageTimeoutUnit) {
    mMyRegistrarKey = myRegistrarKey;
    mID = id;
    mPrivateKey = privateKey;
    mRegistrars = registrars;
    mMessageSender = sender;
    mMessageQueue = messageQueue;
    mRandom = cryptoTools.getSecureRandom();
    mExecutor = executor;
    mDigest = cryptoTools.getMessageDigest();
    mSigningEngine = cryptoTools.getSignature();
    mStageTimeout = stageTimeout;
    mStageTimeoutUnit = stageTimeoutUnit;
    mReceivedCommitmentMessages = new HashMap<>();
    mReceivedCommitments = new HashMap<>();
    mReceivedViewMessages = new HashMap<>();
    mReceivedShareKeys = new ArrayList<>();
  }

  @Override
  public Optional<Key> call() {
    try {
      mMyShareKey = Key.newRandomKey(mRandom);
      mCommitmentPair = bitCommitKey(mMyShareKey);
      broadcastCommitment(mCommitmentPair.mCommitment);
      boolean wasWaitSuccessful = waitForCommitments();
      if (!wasWaitSuccessful) {
        return Optional.empty();
      }
      broadcastView();
      wasWaitSuccessful = waitForViews();
      if (!wasWaitSuccessful) {
        return Optional.empty();
      }
      Key generatedKey = combineShareKeys();
      return Optional.of(generatedKey);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (InvalidKeyException | IOException | SignatureException e) {
      LOGGER.error("Exception thrown during signing.", e);
    }
    return Optional.empty();
  }

  private static class CommitmentPair {
    public final byte[] mCommitment;
    public final byte[] mSolution;

    public CommitmentPair(byte[] commitment, byte[] solution) {
      mCommitment = Arrays.copyOf(commitment, commitment.length);
      mSolution = Arrays.copyOf(solution, solution.length);
    }
  }

  private CommitmentPair bitCommitKey(Key myShare) {
    Key nonce = Key.newRandomKey(mRandom);
    byte[] nonceArray = nonce.getBitSet().toByteArray();
    byte[] commitment;
    synchronized (mDigest) {
      mDigest.reset();
      mDigest.update(myShare.getBitSet().toByteArray());
      commitment = mDigest.digest(nonceArray);
    }

    return new CommitmentPair(commitment, nonceArray);
  }

  private void broadcastCommitment(byte[] commitment)
      throws InvalidKeyException, IOException, SignatureException {
    CommitmentMessage msg = new CommitmentMessage(mMyRegistrarKey, mID, commitment);
    mMyCommitmentMessage = new SignedObject(msg, mPrivateKey, mSigningEngine);

    for (Key registrar : mRegistrars) {
      mMessageSender.sendMessage(registrar, mMyCommitmentMessage);
    }
  }

  private void broadcastView()
      throws InvalidKeyException, IOException, SignatureException {
    ViewMessage msg = new ViewMessage(
        mMyRegistrarKey,
        mID,
        mReceivedCommitmentMessages.values(),
        mCommitmentPair.mSolution,
        mMyShareKey);
    SignedObject signedObject = new SignedObject(msg, mPrivateKey, mSigningEngine);

    for (Key registrar : mRegistrars) {
      mMessageSender.sendMessage(registrar, signedObject);
    }
  }

  private Key combineShareKeys() {
    Key finalKey = Key.xor(new Key(0), mMyShareKey);
    for (Key share : mReceivedShareKeys) {
      finalKey = Key.xor(finalKey, share);
    }

    return finalKey;
  }

  private boolean handleRegistrarMessage(SignedObject object)
      throws IOException, ClassNotFoundException {
    RegistrarMessage registrarMessage = (RegistrarMessage) object.getObject();
    if (registrarMessage instanceof CommitmentMessage) {
      CommitmentMessage commit = (CommitmentMessage) registrarMessage;

      for (ViewMessage view : mReceivedViewMessages.values()) {
        if (!view.getCommitments().contains(object)) {
          return false;
        }
      }

      if (mRegistrars.contains(commit.getSender())) {
        mReceivedCommitmentMessages.put(commit.getSender(), object);
        mReceivedCommitments.put(commit.getSender(), commit.getCommitment());
      }
    } else if (registrarMessage instanceof ViewMessage) {
      ViewMessage view = (ViewMessage) registrarMessage;

      if (view.getCommitments().size() != mRegistrars.size() + 1) {
        return false;
      }

      for (SignedObject commitObject : mReceivedCommitmentMessages.values()) {
        if (!view.getCommitments().contains(commitObject)) {
          return false;
        }
      }

      if (!view.getCommitments().contains(mMyCommitmentMessage)) {
        return false;
      }

    }

    return true;
  }

  private boolean waitForCommitments() throws InterruptedException {
    ScheduledFuture timeoutTask = mExecutor.schedule(() -> {
        mIsStage0Timeout = true;
        try {
          mMessageQueue.put(Optional.empty());
        } catch (InterruptedException e) {
          LOGGER.warn("waitForCommitments().Runnable: Unexpected interrupt.", e);
        }
      }, mStageTimeout, mStageTimeoutUnit);

    boolean result = true;

    while (mReceivedCommitments.size() < mRegistrars.size()) {
      Optional<SignedObject> msg = mMessageQueue.take();
      if (!msg.isPresent() && mIsStage0Timeout) {
        result = false;
        break;
      }

      try {
        result = handleRegistrarMessage(msg.get());
        if (!result) {
          break;
        }
      } catch (ClassNotFoundException | IOException e) {
        LOGGER.warn("Exception thrown when getting signed object.", e);
      }
    }

    timeoutTask.cancel(false);
    return result;
  }

  private boolean waitForViews() throws InterruptedException {
    ScheduledFuture timeoutTask = mExecutor.schedule(() -> {
        mIsStage1Timeout = true;
        try {
          mMessageQueue.put(Optional.empty());
        } catch (InterruptedException e) {
          LOGGER.warn("waitForViews().Runnable: Unexpected interrupt.", e);
        }
      }, mStageTimeout, mStageTimeoutUnit);

    boolean result = true;

    while (mReceivedShareKeys.size() < mRegistrars.size()) {
      Optional<SignedObject> msg = mMessageQueue.take();
      if (!msg.isPresent() && mIsStage1Timeout) {
        result = false;
        break;
      }

      try {
        result = handleRegistrarMessage(msg.get());
        if (!result) {
          break;
        }
      } catch (ClassNotFoundException | IOException e) {
        LOGGER.warn("Exception thrown when getting signed object.", e);
      }
    }

    timeoutTask.cancel(false);
    return result;
  }

}
