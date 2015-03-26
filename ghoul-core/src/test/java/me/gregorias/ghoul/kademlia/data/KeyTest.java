package me.gregorias.ghoul.kademlia.data;

import static org.junit.Assert.assertEquals;

import me.gregorias.ghoul.kademlia.data.Key;
import org.junit.Test;

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
}

