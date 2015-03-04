package me.gregorias.ghoul.utils;

import java.nio.ByteBuffer;

/**
 * Utility functions class
 */
public class Utils {
  public static byte[] byteBufferToArray(ByteBuffer buffer) {
    byte[] array = new byte[buffer.position()];
    buffer.get(array);
    return array;
  }

  public static ByteBuffer arrayToByteBuffer(byte[] array) {
    ByteBuffer buffer = ByteBuffer.allocate(array.length);
    buffer.put(array);
    return buffer;
  }
}
