package me.gregorias.ghoul.utils;

import org.junit.Test;

import java.util.BitSet;

import static org.junit.Assert.assertEquals;

public class UtilsTest {
  @Test
  public void bitSetSerializationShouldBeDeserializable() {
    BitSet set = new BitSet();
    set.set(0);
    set.set(7);
    set.set(13);
    set.set(24);

    int byteCount = 10;
    byte[] serializedSet = Utils.serializeBitSet(set, byteCount);
    assertEquals(set, Utils.deserializeBitSet(serializedSet));
    assertEquals(10, serializedSet.length);
  }

  @Test(expected = IllegalArgumentException.class)
  public void bitSetSerializationShouldThrowExceptionOnTooShortArray() {
    BitSet set = new BitSet();
    set.set(0);
    set.set(7);
    set.set(13);
    set.set(24);
    Utils.serializeBitSet(set, 1);
  }
}
