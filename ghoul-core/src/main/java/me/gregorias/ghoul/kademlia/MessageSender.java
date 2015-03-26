package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.KademliaMessage;

import java.net.InetSocketAddress;

interface MessageSender {
  void sendMessage(InetSocketAddress dest, KademliaMessage msg);
}
