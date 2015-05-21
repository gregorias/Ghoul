package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public final class ViewMessage extends RegistrarMessage {
  private static final long serialVersionUID = 1L;

  private final Collection<CommitmentMessage> mCommitments;
  private final byte[] mSolution;
  private final Key mShareKey;

  public ViewMessage(
      Key sender,
      PublicKey pubKey,
      Collection<CommitmentMessage> commitments,
      byte[] solution,
      Key shareKey) {
    super(sender, pubKey);
    mCommitments = new ArrayList<>(commitments);
    mSolution = Arrays.copyOf(solution, solution.length);
    mShareKey = shareKey;
  }

  public Collection<CommitmentMessage> getCommitments() {
    return new ArrayList<>(mCommitments);
  }

  public Key getShareKey() {
    return mShareKey;
  }

  public byte[] getSolution() {
    return Arrays.copyOf(mSolution, mSolution.length);
  }
}
