package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.security.Certificate;
import me.gregorias.ghoul.utils.DeserializationException;
import me.gregorias.ghoul.utils.Utils;

import javax.validation.constraints.NotNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Message sent between kademlia hosts.
 */
public class KademliaMessage {
  private final NodeInfo mSrcNodeInfo;
  private final NodeInfo mDestNodeInfo;
  private final boolean mCertificateRequest;
  private final Collection<Certificate> mCertificates;
  private Optional<byte[]> mSignature;
  private final int mId;

  public KademliaMessage(@NotNull NodeInfo src, @NotNull NodeInfo dest, int id) {
    mSrcNodeInfo = src;
    mDestNodeInfo = dest;
    mCertificateRequest = false;
    mCertificates = new ArrayList<>();
    mSignature = Optional.empty();
    mId = id;
  }

  public KademliaMessage(@NotNull NodeInfo src,
                         @NotNull NodeInfo dest,
                         int id,
                         boolean certificateRequest,
                         Collection<Certificate> certificates) {
    mSrcNodeInfo = src;
    mDestNodeInfo = dest;
    mCertificateRequest = certificateRequest;
    mCertificates = new ArrayList<>(certificates);
    mSignature = Optional.empty();
    mId = id;
  }

  public KademliaMessage(@NotNull NodeInfo src,
                         @NotNull NodeInfo dest,
                         int id,
                         boolean certificateRequest,
                         Collection<Certificate> certificates,
                         byte[] signature) {
    mSrcNodeInfo = src;
    mDestNodeInfo = dest;
    mCertificateRequest = certificateRequest;
    mCertificates = new ArrayList<>(certificates);
    mSignature = Optional.of(Arrays.copyOf(signature, signature.length));
    mId = id;
  }

  public Collection<Certificate> getCertificates() {
    return mCertificates;
  }

  public NodeInfo getDestinationNodeInfo() {
    return mDestNodeInfo;
  }

  public Optional<byte[]> getSignature() {
    return mSignature;
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

  public void setSignature(byte[] signature) {
    mSignature = Optional.of(Arrays.copyOf(signature, signature.length));
  }

  public void signMessage(Object privateKey) {
    // TODO
  }

  public boolean verifyMessage(Object publicKey) {
    return true;
  }

  public void serialize(ByteBuffer buffer) {
    getSourceNodeInfo().serialize(buffer);
    getDestinationNodeInfo().serialize(buffer);
    buffer.putInt(getId());
    buffer.put((byte) (isCertificateRequest() ? 1 : 0));
    Utils.serializeSerializable(new ArrayList<>(getCertificates()), buffer);
    if (mSignature.isPresent()) {
      buffer.put((byte) 1);
      buffer.putInt(mSignature.get().length);
      buffer.put(mSignature.get());
    } else {
      buffer.put((byte) 0);
    }
  }

  @SuppressWarnings("unchecked")
  public static KademliaMessage deserialize(ByteBuffer buffer) throws DeserializationException {
    NodeInfo srcNodeInfo = NodeInfo.deserialize(buffer);
    NodeInfo destNodeInfo = NodeInfo.deserialize(buffer);

    try {
      int id = buffer.getInt();
      boolean certificateRequest = buffer.get() == 1 ? true : false;
      Collection<Certificate> certificates =
          (Collection<Certificate>) Utils.deserializeSerializable(buffer);
      KademliaMessage msg = new KademliaMessage(srcNodeInfo,
          destNodeInfo,
          id,
          certificateRequest,
          certificates);
      if (buffer.get() == 1) {
        int length = buffer.getInt();
        byte[] signature = new byte[length];
        buffer.get(signature);
        msg.signMessage(signature);
      }
      return msg;
    } catch (BufferUnderflowException | ClassCastException e) {
      throw new DeserializationException(e);
    }
  }
}
