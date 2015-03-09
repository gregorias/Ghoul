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
    byte[] serializedSet = Utils.serializeBitSet(set, 10);
    assertEquals(set, Utils.deserializeBitSet(serializedSet));
  }
}
