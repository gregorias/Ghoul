package me.gregorias.ghoul.security;

import me.gregorias.ghoul.kademlia.data.Key;

import java.io.Serializable;

public class RegistrarMessage implements Serializable {
  private static final long serialVersionUID = 1L;
  private final Key mSender;
  private final int mId;

  public RegistrarMessage(Key sender, int id) {
    mSender = sender;
    mId = id;
  }

  public Key getSender() {
    return mSender;
  }

  public int getId() {
    return mId;
  }
}
