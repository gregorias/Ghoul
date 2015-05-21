package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.security.PublicKey;
import java.util.Arrays;

public final class CommitmentMessage extends RegistrarMessage {
  private static final long serialVersionUID = 1L;

  private final byte[] mCommitment;

  public CommitmentMessage(Key sender, PublicKey pubKey, byte[] commitment) {
    super(sender, pubKey);
    mCommitment = Arrays.copyOf(commitment, commitment.length);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || getClass() != object.getClass()) {
      return false;
    }

    CommitmentMessage that = (CommitmentMessage) object;

    return  getSender().equals(that.getSender())
        && getClientPublicKey().equals(that.getClientPublicKey())
        && Arrays.equals(mCommitment, that.mCommitment);

  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(mCommitment);
  }

  public byte[] getCommitment() {
    return Arrays.copyOf(mCommitment, mCommitment.length);
  }
}
