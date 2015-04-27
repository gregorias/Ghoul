package me.gregorias.ghoul.kademlia;

import me.gregorias.ghoul.kademlia.data.KademliaMessage;

public interface MessageMatcher {
  boolean match(KademliaMessage msg);
}
