package me.gregorias.ghoul.kademlia;

/**
 * Message Listener for Kademlia messages.
 */
interface MessageListener {
  /**
   * Method to be called when a find node message is received.
   */
  void receiveFindNodeMessage(FindNodeMessage msg);

  /**
   * Method to be called when a find node reply message is received.
   */
  void receiveFindNodeReplyMessage(FindNodeReplyMessage msg);

  /**
   * Method to be called when a ping message is received.
   */
  void receivePingMessage(PingMessage msg);


  /**
   * Method to be called when a pong message is received.
   */
  void receivePongMessage(PongMessage msg);
}

