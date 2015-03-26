package me.gregorias.ghoul.kademlia.data;

import static org.junit.Assert.assertEquals;

import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.utils.DeserializationException;
import org.junit.Test;

import java.nio.ByteBuffer;

public final class KeyTest {

  @Test
  public void shouldReturnCorrectDistance() {
    Key zero = new Key(0);
    Key one = new Key(1);
    assertEquals(0, zero.getDistanceBit(one));
  }

  @Test
  public void shouldReturnCorrectDistance2() {
    Key one = new Key(1);
    Key two = new Key(2);
    assertEquals(1, one.getDistanceBit(two));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowExceptionWhenProvidedNegativeNumber() {
    new Key(-1);
  }

  @Test(expected = DeserializationException.class)
  public void shouldThrowDeserializationExceptionWhenProvidedEmptyBuffer()
      throws DeserializationException {
    ByteBuffer buffer = ByteBuffer.allocate(4);
    buffer.putInt(1);
    buffer.flip();
    Key.deserialize(buffer);
  }
}

