package me.gregorias.ghoul.network.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;

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

  private ByteListener mListener;

  public UDPByteListeningService(DatagramChannel datagramChannel,
                                 int localPort,
                                 ExecutorService executor) {
    mDatagramChannel = datagramChannel;
    mLocalPort = localPort;
    mServiceExecutor = executor;
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
    ByteBuffer recvBuffer = ByteBuffer.allocate(MAXIMUM_PACKET_SIZE);
    while (mDatagramChannel.isOpen()) {
      InetSocketAddress inetSender;
      byte[] msg;
      try {
        SocketAddress sender = mDatagramChannel.receive(recvBuffer);
        inetSender = (InetSocketAddress) sender;
        msg = byteBufferToArray(recvBuffer);
        recvBuffer.clear();
      } catch (IOException e) {
        if (mDatagramChannel.isOpen()) {
          LOGGER.error("run()", e);
        }
        continue;
      } catch (ClassCastException e) {
        LOGGER.error("run()", e);
        continue;
      }

      if (mListener != null) {
        mListener.receiveMessage(inetSender, msg);
      }
    }
  }

  public void start() throws IOException {
    LOGGER.debug("start()");
    SocketAddress localAddress = InetSocketAddress.createUnresolved("0.0.0.0", mLocalPort);;
    mDatagramChannel.bind(localAddress);
    mServiceExecutor.execute(this);
  }

  public void stop() {
    LOGGER.debug("stop()");
    try {
      mDatagramChannel.close();
    } catch (IOException e) {
      LOGGER.error("stop()", e);
    }
  }
}
