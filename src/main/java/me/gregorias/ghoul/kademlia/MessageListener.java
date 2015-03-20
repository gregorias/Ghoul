package me.gregorias.ghoul.kademlia;

/**
 * Message Listener for Kademlia messages.
 */
interface MessageListener {

  /**
   * Default message receipt method which checks concrete class of the incoming message and puts
   * into corresponding method.
   *
   * @param msg received message
   */
  default void receive(KademliaMessage msg) {
    if (msg instanceof FindNodeMessage) {
      receiveFindNodeMessage((FindNodeMessage) msg);
    } else if (msg instanceof FindNodeReplyMessage) {
      receiveFindNodeReplyMessage((FindNodeReplyMessage) msg);
    } else if (msg instanceof PingMessage) {
      receivePingMessage((PingMessage) msg);
    } else if (msg instanceof PongMessage) {
      receivePongMessage((PongMessage) msg);
    } else {
      throw new IllegalArgumentException("Unexpected message type.");
    }
  }

  /**
   * Method to be called when a find node message is received.
   *
   * @param msg received message
   */
  void receiveFindNodeMessage(FindNodeMessage msg);

  /**
   * Method to be called when a find node reply message is received.
   *
   * @param msg received message
   */
  void receiveFindNodeReplyMessage(FindNodeReplyMessage msg);

  /**
   * Method to be called when a ping message is received.
   *
   * @param msg received message
   */
  void receivePingMessage(PingMessage msg);


  /**
   * Method to be called when a pong message is received.
   *
   * @param msg received message
   */
  void receivePongMessage(PongMessage msg);
}

