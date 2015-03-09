package me.gregorias.ghoul.utils;

import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * Utility functions class
 */
public class Utils {
  private static final int BITS_IN_BYTE = 8;

  public static ByteBuffer arrayToByteBuffer(byte[] array) {
    ByteBuffer buffer = ByteBuffer.allocate(array.length);
    buffer.put(array);
    return buffer;
  }

  public static byte[] byteBufferToArray(ByteBuffer buffer) {
    byte[] array = new byte[buffer.position()];
    buffer.get(array);
    return array;
  }

  public static byte[] serializeBitSet(BitSet set, int byteLength) {
    assert byteLength * BITS_IN_BYTE >= set.length();
    byte[] serializedBitSet = new byte[byteLength];
    for (int i = 0; i < set.length(); ++i) {
      if (set.get(i)) {
        byte currentBit = ((byte) (1 << (i % BITS_IN_BYTE)));
        serializedBitSet[i / BITS_IN_BYTE] |= currentBit;
      }
    }
    return serializedBitSet;
  }

  public static BitSet deserializeBitSet(byte[] serializedBitSet) {
    BitSet bitSet = new BitSet();
    for (int i = 0; i < serializedBitSet.length; ++i) {
      for (int j = 0; j < BITS_IN_BYTE; ++j) {
        if ((serializedBitSet[i] & (1 << j)) != 0) {
          bitSet.set(i * BITS_IN_BYTE + j);
        }
      }
    }
    return bitSet;
  }

}
