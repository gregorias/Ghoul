package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.KademliaMessage;

/**
 * Message Listener for Kademlia messages.
 */
interface MessageListener {

  /**
   * Method to be called when a kademlia message is received.
   *
   * @param msg received message
   */
  void receive(KademliaMessage msg);
}

