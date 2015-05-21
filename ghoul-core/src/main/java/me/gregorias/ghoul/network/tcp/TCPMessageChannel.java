package me.gregorias.ghoul.network.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class TCPMessageChannel {
  private static final Logger LOGGER = LoggerFactory.getLogger(TCPMessageChannel.class);
  private final Socket mSocket;
  private final OutputStream mOS;
  private final ObjectOutputStream mOOS;
  private final ObjectInputStream mOIS;

  private TCPMessageChannel(Socket socket) throws IOException {
    mSocket = socket;
    mOS = socket.getOutputStream();
    mOOS = new ObjectOutputStream(mOS);
    mOIS = new ObjectInputStream(socket.getInputStream());
  }

  public void close() throws IOException {
    try {
      mOIS.close();
      mOOS.close();
    } catch (IOException e) {
      LOGGER.warn("close()", e);
    }
    mSocket.close();
  }

  public static TCPMessageChannel create(InetSocketAddress destination) throws IOException {
    Socket socket = new Socket(destination.getAddress(), destination.getPort());
    return new TCPMessageChannel(socket);
  }

  public static TCPMessageChannel create(Socket socket) throws IOException {
    return new TCPMessageChannel(socket);
  }

  public Object receiveMessage() throws ClassNotFoundException, IOException {
    return mOIS.readObject();
  }

  public Optional<Object> receiveMessage(long timeout, TimeUnit unit)
      throws ClassNotFoundException, IOException {
    LOGGER.trace("receiveMessage()");
    mSocket.setSoTimeout((int) unit.toMillis(timeout));
    try {
      return Optional.of(mOIS.readObject());
    } catch (SocketTimeoutException e) {
      return Optional.empty();
    } finally {
      mSocket.setSoTimeout(0);
    }
  }

  public void sendMessage(Serializable message) throws IOException {
    mOOS.writeObject(message);
    mOOS.flush();
    mOS.flush();
  }
}
