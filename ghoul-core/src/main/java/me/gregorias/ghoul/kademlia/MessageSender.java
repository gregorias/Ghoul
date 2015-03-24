package me.gregorias.ghoul.kademlia;

import java.net.InetSocketAddress;

interface MessageSender {
  void sendMessage(InetSocketAddress dest, KademliaMessage msg);
}
