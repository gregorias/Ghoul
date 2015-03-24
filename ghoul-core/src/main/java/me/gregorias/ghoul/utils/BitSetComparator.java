package me.gregorias.ghoul.utils;

import java.io.Serializable;
import java.util.BitSet;
import java.util.Comparator;

/**
 * Comparator of BitSets representing a 160-bit number in a little-endian
 * encoding.
 *
 * @author Grzegorz Milka
 */
public class BitSetComparator implements Comparator<BitSet>, Serializable {
  private static final long serialVersionUID = 1L;
  public static final BitSetComparator COMPARATOR = new BitSetComparator();

  @Override
  public int compare(BitSet b1, BitSet b2) {
    int lengthComparison = Integer.compare(b1.length(), b2.length());
    if (lengthComparison != 0) {
      return lengthComparison;
    }

    int bitIdx = b1.length();
    while (bitIdx > 0 && b1.previousSetBit(bitIdx - 1) == b2.previousSetBit(bitIdx - 1)) {
      bitIdx = b1.previousSetBit(bitIdx - 1);
    }
    if (bitIdx == 0 || bitIdx == -1) {
      return 0;
    } else {
      assert bitIdx > 0;
      return Integer.compare(b1.previousSetBit(bitIdx - 1), b2.previousSetBit(bitIdx - 1));
    }
  }
}
