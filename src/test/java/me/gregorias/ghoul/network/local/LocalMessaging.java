package me.gregorias.ghoul.network.local;

import me.gregorias.ghoul.network.ByteListener;
import me.gregorias.ghoul.network.ByteListeningService;
import me.gregorias.ghoul.network.ByteSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of local messaging system. It implements network interfaces for local in-memory
 * communication allowing reliable unit tests.
 *
 * @author Grzegorz Milka
 */
public class LocalMessaging {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalMessaging.class);
  private final ReadWriteLock mRWLock;
  private final Lock mReadLock;
  private final Lock mWriteLock;
  private final Map<Integer, ByteListener> mPortToListenerMap;

  public LocalMessaging() {
    mRWLock = new ReentrantReadWriteLock();
    mReadLock = mRWLock.readLock();
    mWriteLock = mRWLock.writeLock();
    mPortToListenerMap = new HashMap<Integer, ByteListener>();
  }

  public ByteListeningService getByteListeningService(int port) {
    return new ByteListeningServiceImpl(port);
  }

  public ByteSender getByteSender(int sourcePort) {
    return new ByteSenderImpl(sourcePort);
  }

  private class ByteListeningServiceImpl implements ByteListeningService {
    private final int mPort;

    public ByteListeningServiceImpl(int port) {
      mPort = port;
    }

    @Override
    public void registerListener(ByteListener listener) {
      LOGGER.debug("registerListener(): port: {}", mPort);
      mWriteLock.lock();
      try {
        if (mPortToListenerMap.containsKey(mPort)) {
          throw new IllegalStateException(String.format("Port: %d already has a listener", mPort));
        }
        mPortToListenerMap.put(mPort, listener);
      } finally {
        mWriteLock.unlock();
      }
    }

    @Override
    public void unregisterListener(ByteListener listener) {
      LOGGER.debug("unregisterListener(): port: {}", mPort);
      mWriteLock.lock();
      try {
        ByteListener byteListener = mPortToListenerMap.get(mPort);
        if (byteListener == null) {
          throw new IllegalStateException(String.format("Port: %d has no listener", mPort));
        } else if (byteListener != listener) {
          throw new IllegalStateException("Given listener hasn't been registered.");
        }
        mPortToListenerMap.remove(mPort);
      } finally {
        mWriteLock.unlock();
      }
    }
  }

  private class ByteSenderImpl implements ByteSender {
    private final InetSocketAddress mSrcAddress;

    public ByteSenderImpl(int srcPort) {
      mSrcAddress = new InetSocketAddress(srcPort);
    }

    @Override
    public void sendMessage(InetSocketAddress dest, byte[] msg) {
      mReadLock.lock();
      try {
        ByteListener byteListener = mPortToListenerMap.get(dest.getPort());
        if (byteListener == null) {
          LOGGER.debug("Listener for {} port does not exist.", dest.getPort());
        } else {
          byteListener.receiveMessage(mSrcAddress, msg);
        }
      } finally {
        mReadLock.unlock();
      }
    }
  }
}

