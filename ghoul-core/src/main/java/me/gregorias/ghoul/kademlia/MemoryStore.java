package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.Key;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of Store which keeps all items in memory.
 */
public class MemoryStore implements Store {
  private final Map<Key, byte[]> mStore;

  public MemoryStore() {
    mStore = new HashMap<>();
  }

  @Override
  public boolean contains(Key key) {
    return mStore.containsKey(key);
  }

  @Override
  public Optional<byte[]> get(Key key) {
    byte[] value = mStore.get(key);

    if (value == null) {
      return Optional.empty();
    } else {
      return Optional.of(value);
    }
  }

  @Override
  public void put(Key key, byte[] value) {
    mStore.put(key, value);
  }
}
