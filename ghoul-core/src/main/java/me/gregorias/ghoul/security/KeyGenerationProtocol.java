package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
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
  private final PublicKey mClientPublicKey;
  private final PrivateKey mPrivateKey;
  private final Collection<Key> mRegistrars;
  private final RegistrarMessageSender mMessageSender;
  private final BlockingQueue<Optional<SignedObject>> mMessageQueue;
  private final SecureRandom mRandom;
  private final ScheduledExecutorService mExecutor;
  private final CryptographyTools mCryptoTools;

  private final long mStageTimeout;
  private final TimeUnit mStageTimeoutUnit;

  private final Map<Key, CommitmentMessage> mReceivedCommitmentMessages;
  private final Map<Key, ViewMessage> mReceivedViewMessages;
  private final Collection<Key> mReceivedShareKeys;

  private CommitmentMessage mMyCommitmentMessage;


  private boolean mIsStage0Timeout = false;
  private boolean mIsStage1Timeout = false;

  private Key mMyShareKey;
  private CommitmentPair mCommitmentPair;


  public KeyGenerationProtocol(
      Key myRegistrarKey,
      PublicKey clientPublicKey,
      PrivateKey privateKey,
      Collection<Key> registrars,
      RegistrarMessageSender sender,
      BlockingQueue<Optional<SignedObject>> messageQueue,
      ScheduledExecutorService executor,
      CryptographyTools cryptoTools,
      long stageTimeout,
      TimeUnit stageTimeoutUnit) {
    mMyRegistrarKey = myRegistrarKey;
    mClientPublicKey = clientPublicKey;
    mPrivateKey = privateKey;
    mRegistrars = registrars;
    mMessageSender = sender;
    mMessageQueue = messageQueue;
    mRandom = cryptoTools.getSecureRandom();
    mExecutor = executor;
    mCryptoTools = cryptoTools;
    mStageTimeout = stageTimeout;
    mStageTimeoutUnit = stageTimeoutUnit;
    mReceivedCommitmentMessages = new HashMap<>();
    mReceivedViewMessages = new HashMap<>();
    mReceivedShareKeys = new ArrayList<>();
  }

  @Override
  public Optional<Key> call() {
    try {
      mMyShareKey = Key.newRandomKey(mRandom);
      mCommitmentPair = bitCommitKey(mMyShareKey);
      LOGGER.trace("call(): Broadcasting commitments.");
      broadcastCommitment(mCommitmentPair.mCommitment);
      LOGGER.trace("call(): Waiting for commitments.");
      boolean wasWaitSuccessful = waitForCommitments();
      if (!wasWaitSuccessful) {
        return Optional.empty();
      }
      LOGGER.trace("call(): Broadcasting view.");
      broadcastView();
      LOGGER.trace("call(): Waiting for views.");
      wasWaitSuccessful = waitForViews();
      if (!wasWaitSuccessful) {
        LOGGER.trace("call() -> Wait for views was unsuccessful.");
        return Optional.empty();
      }
      LOGGER.trace("call(): Combining shares.");
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
    commitment = mCryptoTools.digestMessage(myShare.getBitSet().toByteArray());

    return new CommitmentPair(commitment, nonceArray);
  }

  private void broadcastCommitment(byte[] commitment)
      throws InvalidKeyException, IOException, SignatureException {
    mMyCommitmentMessage = new CommitmentMessage(mMyRegistrarKey, mClientPublicKey, commitment);
    SignedObject signedMsg = mCryptoTools.signObject(mMyCommitmentMessage, mPrivateKey);

    for (Key registrar : mRegistrars) {
      mMessageSender.sendMessage(registrar, signedMsg);
    }
  }

  private void broadcastView()
      throws InvalidKeyException, IOException, SignatureException {
    Collection<CommitmentMessage> commitments = new ArrayList<>(
        mReceivedCommitmentMessages.values());
    commitments.add(mMyCommitmentMessage);
    ViewMessage msg = new ViewMessage(
        mMyRegistrarKey,
        mClientPublicKey,
        commitments,
        mCommitmentPair.mSolution,
        mMyShareKey);
    SignedObject signedObject = mCryptoTools.signObject(msg, mPrivateKey);

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
      if (!mRegistrars.contains(commit.getSender())) {
        return true;
      }

      for (ViewMessage view : mReceivedViewMessages.values()) {
        if (!view.getCommitments().contains(commit)) {
          return false;
        }
      }

      mReceivedCommitmentMessages.put(commit.getSender(), commit);
    } else if (registrarMessage instanceof ViewMessage) {
      ViewMessage view = (ViewMessage) registrarMessage;
      if (!mRegistrars.contains(view.getSender())) {
        return true;
      }

      if (mReceivedViewMessages.containsKey(view.getSender())) {
        return true;
      }

      if (view.getCommitments().size() != mRegistrars.size() + 1) {
        LOGGER.warn("handleRegistrarMessage(): Received ViewMessage has wrong number of"
            + " commitments: {}.", view.getCommitments().size());
        return false;
      }

      for (CommitmentMessage commit : mReceivedCommitmentMessages.values()) {
        if (!view.getCommitments().contains(commit)) {
          LOGGER.warn("handleRegistrarMessage(): Received ViewMessage does not contain a"
              + " commitment.");
          return false;
        }
      }

      if (!view.getCommitments().contains(mMyCommitmentMessage)) {
        LOGGER.warn("handleRegistrarMessage(): Received ViewMessage does not contain this node's"
            + " commitment.");
        return false;
      }

      mReceivedViewMessages.put(view.getSender(), view);
      mReceivedShareKeys.add(view.getShareKey());
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

    while (mReceivedCommitmentMessages.size() < mRegistrars.size()) {
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
        LOGGER.trace("waitForViews(): Timing out wait for views.");
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
