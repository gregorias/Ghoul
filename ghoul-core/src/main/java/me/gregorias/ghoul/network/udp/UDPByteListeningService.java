package me.gregorias.ghoul.network.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import me.gregorias.ghoul.network.ByteListener;
import me.gregorias.ghoul.network.ByteListeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static me.gregorias.ghoul.utils.Utils.byteBufferToArray;

/**
 * Implementation of {@link me.gregorias.ghoul.network.ByteListeningService} which creates a
 * ServerSocket and listens on a single thread.
 *
 * @author Grzegorz Milka
 */
public final class UDPByteListeningService implements ByteListeningService, Runnable {
  public static final int MAXIMUM_PACKET_SIZE = (1 << 16) - 1;
  private static final Logger LOGGER = LoggerFactory.getLogger(UDPByteListeningService.class);

  private final DatagramChannel mDatagramChannel;
  private final int mLocalPort;
  private final ExecutorService mServiceExecutor;

  /**
   * In this queue the run() method will put its thread to enable interrupt signals.
   */
  private final BlockingQueue<Thread> mThreadQueue;
  private Future<?> mMainThreadFuture;

  private ByteListener mListener;

  public UDPByteListeningService(DatagramChannel datagramChannel,
                                 int localPort,
                                 ExecutorService executor) {
    mDatagramChannel = datagramChannel;
    mLocalPort = localPort;
    mServiceExecutor = executor;

    mThreadQueue = new LinkedBlockingQueue<>();
  }

  @Override
  public synchronized void registerListener(ByteListener listener) {
    assert mListener == null;
    mListener = listener;
  }

  @Override
  public synchronized void unregisterListener(ByteListener listener) {
    assert mListener == listener;
    mListener = null;
  }

  @Override
  public void run() {
    LOGGER.debug("run()");

    try {
      mThreadQueue.put(Thread.currentThread());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.debug("run() -> void. Unexpected interrupt");
      return;
    }

    ByteBuffer recvBuffer = ByteBuffer.allocate(MAXIMUM_PACKET_SIZE);
    while (!Thread.currentThread().isInterrupted()) {
      InetSocketAddress inetSender;
      byte[] msg;
      try {
        SocketAddress sender = mDatagramChannel.receive(recvBuffer);
        inetSender = (InetSocketAddress) sender;
        recvBuffer.flip();
        msg = byteBufferToArray(recvBuffer);
      } catch (ClosedByInterruptException e) {
        break;
      } catch (IOException e) {
        LOGGER.trace("run()", e);
        continue;
      } catch (ClassCastException e) {
        LOGGER.trace("run(): Received sender address is not of InetSocketAddress class", e);
        continue;
      } finally {
        recvBuffer.clear();
      }

      synchronized (this) {
        if (mListener != null) {
          mListener.receiveMessage(inetSender, msg);
        }
      }
    }

    LOGGER.debug("run() -> void");
  }

  public void start() throws IOException {
    LOGGER.debug("start()");
    SocketAddress localAddress = new InetSocketAddress(mLocalPort);
    mDatagramChannel.bind(localAddress);
    mMainThreadFuture = mServiceExecutor.submit(this);
  }

  public void stop() {
    LOGGER.debug("stop()");
    Thread runThread;
    try {
      runThread = mThreadQueue.take();
      runThread.interrupt();
      mMainThreadFuture.get();
    } catch (InterruptedException e) {
      LOGGER.error("stop(): Unexpected interrupt.", e);
      Thread.currentThread().interrupt();
      return;
    } catch (ExecutionException e) {
      LOGGER.error("stop()", e);
    }

    if (mDatagramChannel.isOpen()) {
      try {
        mDatagramChannel.close();
      } catch (IOException e) {
        LOGGER.error("stop(): IOException thrown when closing the DatagramChannel.", e);
      }
    }
    LOGGER.debug("stop() -> void");
  }
}
