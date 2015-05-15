package me.gregorias.ghoul.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * Utility functions class
 */
public class Utils {
  private static final int BITS_IN_BYTE = 8;
  private static final byte IPV4_TAG = 4;
  private static final int IPV4_BYTE_LENGTH = 4;
  private static final byte IPV6_TAG = 6;
  private static final int IPV6_BYTE_LENGTH = 16;

  public static ByteBuffer arrayToByteBuffer(byte[] array) {
    ByteBuffer buffer = ByteBuffer.allocate(array.length);
    buffer.put(array);
    buffer.flip();
    return buffer;
  }

  public static byte[] byteBufferToArray(ByteBuffer buffer) {
    byte[] array = new byte[buffer.limit()];
    buffer.get(array);
    return array;
  }

  public static byte[] serializeBitSet(BitSet set, int byteLength) {
    if (byteLength * BITS_IN_BYTE < set.length()) {
      throw new IllegalArgumentException();
    }
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

  public static void serializeInetAddress(InetAddress inetAddress, ByteBuffer buffer) {
    byte[] address = inetAddress.getAddress();
    if (address.length == IPV4_BYTE_LENGTH) {
      if (buffer.capacity() - buffer.position() < (1 + IPV4_BYTE_LENGTH)) {
        throw new IllegalArgumentException("Insufficient buffer space.");
      }
      buffer.put(IPV4_TAG);
      buffer.put(address);
    } else if (address.length == IPV6_BYTE_LENGTH) {
      if (buffer.capacity() - buffer.position() < (1 + IPV6_BYTE_LENGTH)) {
        throw new IllegalArgumentException("Insufficient buffer space.");
      }
      buffer.put(IPV6_TAG);
      buffer.put(address);
    } else {
      throw new IllegalArgumentException("Unknown type of inet address.");
    }
  }

  public static InetAddress deserializeInetAddress(ByteBuffer buffer)
      throws DeserializationException {
    byte[] address;
    try {
      byte ipTag = buffer.get();
      switch (ipTag) {
        case IPV4_TAG:
          address = new byte[IPV4_BYTE_LENGTH];
          buffer.get(address);
          break;
        case IPV6_TAG:
          address = new byte[IPV6_BYTE_LENGTH];
          buffer.get(address);
          break;
        default:
          throw new DeserializationException("Unknown message tag.");
      }
      return InetAddress.getByAddress(address);
    } catch (BufferUnderflowException e) {
      throw new DeserializationException("Buffer has insufficient elements.");
    } catch (UnknownHostException e) {
      throw new IllegalStateException("Unexpected UnknownHostException");
    }
  }

  public static void serializeInetSocketAddress(InetSocketAddress socketAddress,
                                                ByteBuffer buffer) {
    serializeInetAddress(socketAddress.getAddress(), buffer);
    buffer.putShort((short) socketAddress.getPort());
  }

  public static InetSocketAddress deserializeInetSocketAddress(ByteBuffer buffer)
      throws DeserializationException {
    InetAddress address = deserializeInetAddress(buffer);
    short port;
    try {
      port = buffer.getShort();
    } catch (BufferUnderflowException e) {
      throw new DeserializationException(e);
    }
    return new InetSocketAddress(address, port);
  }

  public static void serializeSerializable(Serializable object,
                                           ByteBuffer buffer) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(object);
      oos.close();
      baos.close();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    byte[] result = baos.toByteArray();
    buffer.putInt(result.length);
    buffer.put(result);
  }

  public static Object deserializeSerializable(ByteBuffer buffer) throws DeserializationException {
    try {
      int length = buffer.getInt();
      byte[] result = new byte[length];
      buffer.get(result);
      ByteArrayInputStream bais = new ByteArrayInputStream(result);
      ObjectInputStream ois = new ObjectInputStream(bais);
      return ois.readObject();
    } catch (BufferUnderflowException | ClassNotFoundException | IOException e) {
      throw new DeserializationException(e);
    }
  }
}
