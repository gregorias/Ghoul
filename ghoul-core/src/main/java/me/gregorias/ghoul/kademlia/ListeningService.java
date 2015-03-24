package me.gregorias.ghoul.kademlia;

/**
 * Objects with this interface listen for messages and forward them to
 * registered listener.
 *
 * @author Grzegorz Milka
 */
interface ListeningService {
  void registerListener(MessageListener listener);

  /**
   * After call to this method no previously registered listener will be called.
   *
   * @param listener listener to unregister.
   */
  void unregisterListener(MessageListener listener);
}

