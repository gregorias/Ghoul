package me.gregorias.ghoul.kademlia.data;

import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.utils.BitSetComparator;

import java.io.Serializable;
import java.util.BitSet;
import java.util.Comparator;

/**
 * Comparator of Kademlia {@link Key} based on closeness to specified reference key.
 *
 * @author Grzegorz Milka
 */
public final class KeyComparator implements Comparator<Key>, Serializable {
  private static final long serialVersionUID = 1L;
  private final Key mReferenceKey;

  public KeyComparator(Key key) {
    mReferenceKey = key;
  }

  @Override
  public int compare(Key arg0, Key arg1) {
    BitSet distance0 = mReferenceKey.calculateDistance(arg0);
    BitSet distance1 = mReferenceKey.calculateDistance(arg1);

    return BitSetComparator.COMPARATOR.compare(distance0, distance1);
  }
}
