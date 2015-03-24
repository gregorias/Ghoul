package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.utils.BitSetComparator;

import java.io.Serializable;
import java.util.BitSet;
import java.util.Comparator;

/**
 * Comparator of Kademlia {@link me.gregorias.ghoul.kademlia.Key} with specified key based
 * on closeness.
 *
 * @author Grzegorz Milka
 */
class KeyComparator implements Comparator<Key>, Serializable {
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
