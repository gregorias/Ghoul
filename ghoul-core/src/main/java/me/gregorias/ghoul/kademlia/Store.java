package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.Key;

import java.util.Optional;

/**
 * Storage for key-value pairs.
 */
public interface Store {
  boolean contains(Key key);

  Optional<byte[]> get(Key key);

  void put(Key key, byte[] value);
}
