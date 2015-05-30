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
  private static final int MAX_SEND_RETRIES = 3;
  private static final long SEND_TIMEOUT = 1000;
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
    LOGGER.trace("create({})", destination);
    Socket socket;
    try {
      socket = new Socket(destination.getAddress(), destination.getPort());
    } catch (IOException e) {
      throw e;
    } catch (RuntimeException e) {
      LOGGER.error("create({}): Caught exception during socket creation to address: {}, port: {}",
          destination,
          destination.getAddress(),
          destination.getPort(),
          e);
      throw e;
    }
    return new TCPMessageChannel(socket);
  }

  public static TCPMessageChannel create(Socket socket) throws IOException {
    LOGGER.trace("create({})", socket);
    return new TCPMessageChannel(socket);
  }

  public Object receiveMessage() throws ClassNotFoundException, IOException {
    try {
      LOGGER.trace("receiveMessage()");
      Object readObject = mOIS.readObject();
      LOGGER.trace("receiveMessage() -> Received message.");
      return readObject;
    } catch (ClassNotFoundException | IOException e) {
      LOGGER.error("receiveMessage(): Could not read object.", e);
      throw e;
    } catch (Exception e) {
      LOGGER.error("receiveMessage(): Could not read object.", e);
      throw new IOException(e);
    }
  }

  public Optional<Object> receiveMessage(long timeout, TimeUnit unit)
      throws ClassNotFoundException, IOException {
    LOGGER.trace("receiveMessage(timeout={}, unit={})", timeout, unit);
    mSocket.setSoTimeout((int) unit.toMillis(timeout));
    try {
      Optional<Object> readObject = Optional.of(mOIS.readObject());
      LOGGER.trace("receiveMessage(timeout={}, unit={}) -> Received message.", timeout, unit);
      return readObject;
    } catch (SocketTimeoutException e) {
      return Optional.empty();
    } finally {
      mSocket.setSoTimeout(0);
    }
  }

  public void sendMessage(Serializable message) throws IOException {
    int tries = 0;
    IOException lastException = new IOException();

    while (tries < MAX_SEND_RETRIES) {
      tries += 1;
      try {
        mOOS.writeObject(message);
        mOOS.flush();
        mOS.flush();
        return;
      } catch (IOException e) {
        lastException = e;
        if (tries == MAX_SEND_RETRIES) {
          break;
        }
        LOGGER.trace("sendMessage(): Could not send message due to {}; retrying.", e);
        try {
          Thread.sleep(SEND_TIMEOUT);
        } catch (InterruptedException e1) {
          Thread.currentThread().interrupt();
        }
      }

    }

    throw lastException;
  }
}
