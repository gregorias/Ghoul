package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.util.Arrays;

public final class CommitmentMessage extends RegistrarMessage {
  private static final long serialVersionUID = 1L;

  private final byte[] mCommitment;

  public CommitmentMessage(Key sender, int id, byte[] commitment) {
    super(sender, id);
    mCommitment = Arrays.copyOf(commitment, commitment.length);
  }

  public byte[] getCommitment() {
    return Arrays.copyOf(mCommitment, mCommitment.length);
  }
}
