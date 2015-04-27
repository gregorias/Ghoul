package me.gregorias.ghoul.kademlia;

/**
 * Objects with this interface listen for messages and forward them to
 * registered listener.
 *
 * @author Grzegorz Milka
 */
interface ListeningService {
  void registerListener(MessageMatcher matcher, MessageListener listener);

  void unregisterListener(MessageListener listener);
}

