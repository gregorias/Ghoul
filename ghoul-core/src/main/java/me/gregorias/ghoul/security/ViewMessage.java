package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SignedObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public final class ViewMessage extends RegistrarMessage {
  private static final Logger LOGGER = LoggerFactory.getLogger(ViewMessage.class);
  private static final long serialVersionUID = 1L;

  private final Collection<SignedObject> mCommitments;
  private final byte[] mSolution;
  private final Key mShareKey;

  public ViewMessage(
      Key sender,
      int id,
      Collection<SignedObject> commitments,
      byte[] solution,
      Key shareKey) {
    super(sender, id);
    mCommitments = new ArrayList<>(commitments);
    mSolution = Arrays.copyOf(solution, solution.length);
    mShareKey = shareKey;
  }

  public Collection<SignedObject> getCommitments() {
    return new ArrayList<>(mCommitments);
  }

  public Key getShareKey() {
    return mShareKey;
  }

  public byte[] getSolution() {
    return Arrays.copyOf(mSolution, mSolution.length);
  }
}
