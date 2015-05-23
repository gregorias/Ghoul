package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.kademlia.MessageSerializer;
import me.gregorias.ghoul.security.CryptographyTools;
import me.gregorias.ghoul.security.SignedCertificate;
import me.gregorias.ghoul.utils.DeserializationException;
import me.gregorias.ghoul.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Message sent between kademlia hosts.
 */
public class KademliaMessage {
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaMessage.class);
  private final NodeInfo mSrcNodeInfo;
  private final NodeInfo mDestNodeInfo;
  private final boolean mCertificateRequest;
  private final Collection<SignedCertificate> mCertificates;
  private byte[] mSignature;
  private final int mId;

  public KademliaMessage(@NotNull NodeInfo src, @NotNull NodeInfo dest, int id) {
    mSrcNodeInfo = src;
    mDestNodeInfo = dest;
    mCertificateRequest = false;
    mCertificates = new ArrayList<>();
    mSignature = new byte[0];
    mId = id;
  }

  public KademliaMessage(@NotNull NodeInfo src,
                         @NotNull NodeInfo dest,
                         int id,
                         boolean certificateRequest,
                         Collection<SignedCertificate> certificates) {
    mSrcNodeInfo = src;
    mDestNodeInfo = dest;
    mCertificateRequest = certificateRequest;
    mCertificates = new ArrayList<>(certificates);
    mSignature = new byte[0];
    mId = id;
  }

  public KademliaMessage(@NotNull NodeInfo src,
                         @NotNull NodeInfo dest,
                         int id,
                         boolean certificateRequest,
                         Collection<SignedCertificate> certificates,
                         byte[] signature) {
    mSrcNodeInfo = src;
    mDestNodeInfo = dest;
    mCertificateRequest = certificateRequest;
    mCertificates = new ArrayList<>(certificates);
    mSignature = Arrays.copyOf(signature, signature.length);
    mId = id;
  }

  public Collection<SignedCertificate> getCertificates() {
    return mCertificates;
  }

  public NodeInfo getDestinationNodeInfo() {
    return mDestNodeInfo;
  }

  public byte[] getSignature() {
    return Arrays.copyOf(mSignature, mSignature.length);
  }

  public NodeInfo getSourceNodeInfo() {
    return mSrcNodeInfo;
  }

  public int getId() {
    return mId;
  }

  public boolean isCertificateRequest() {
    return mCertificateRequest;
  }

  public final void serialize(ByteBuffer buffer) {
    serializeContent(buffer);
    buffer.putInt(mSignature.length);
    buffer.put(mSignature);
  }

  public final void signMessage(PrivateKey privateKey, CryptographyTools tools) {
    ByteBuffer buf = ByteBuffer.allocate(MessageSerializer.MAX_MESSAGE_SIZE);
    serializeContent(buf);
    byte[] content = Arrays.copyOf(buf.array(), buf.position());
    try {
      mSignature = tools.signBytes(content, privateKey);
    } catch (InvalidKeyException | IOException | SignatureException e) {
      throw new IllegalStateException(e);
    }
  }

  public final boolean verifyMessage(PublicKey publicKey, CryptographyTools tools) {
    if (mSignature.length == 0) {
      LOGGER.warn("verifyMessage(): This message has no signature.");
      return false;
    }
    ByteBuffer buf = ByteBuffer.allocate(MessageSerializer.MAX_MESSAGE_SIZE);
    serializeContent(buf);
    byte[] content = Arrays.copyOf(buf.array(), buf.position());
    try {
      return tools.verifyBytes(content, mSignature, publicKey);
    } catch (InvalidKeyException | IOException | SignatureException e) {
      LOGGER.error("verifyMessage()", e);
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  public static KademliaMessage deserializeContent(ByteBuffer buffer)
      throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);

    try {
      int id = buffer.getInt();
      boolean certificateRequest = buffer.get() == 1;
      Collection<SignedCertificate> certificates =
          (Collection<SignedCertificate>) Utils.deserializeSerializable(buffer);
      return new KademliaMessage(srcNodeInfo,
          destNodeInfo,
          id,
          certificateRequest,
          certificates);
    } catch (BufferUnderflowException | ClassCastException e) {
      throw new DeserializationException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static byte[] deserializeSignature(ByteBuffer buffer) throws DeserializationException {
    try {
      int length = buffer.getInt();
      byte[] signature = new byte[length];
      buffer.get(signature);
      return signature;
    } catch (BufferUnderflowException | ClassCastException e) {
      throw new DeserializationException(e);
    }
  }

  protected void serializeContent(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
    buffer.put((byte) (isCertificateRequest() ? 1 : 0));
    Utils.serializeSerializable(new ArrayList<>(getCertificates()), buffer);
  }

  protected void setSignature(byte[] signature) {
    mSignature = Arrays.copyOf(signature, signature.length);
  }
}
