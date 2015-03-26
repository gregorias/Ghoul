package me.gregorias.ghoul.network.udp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import me.gregorias.ghoul.network.QueueingByteListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
    "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"})
public final class UDPByteListeningServiceTest {
  private static final int LOCAL_PORT = 1000;
  private static final int REMOTE_PORT = 1001;
  private static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress(REMOTE_PORT);
  private static final byte[] EXAMPLE_MESSAGE = {0, 1, 2, 3};

  private DatagramChannel mMockDatagramChannel;
  private UDPByteListeningService mService;

  @Rule
  public Timeout mGlobalTimeout = new Timeout(1, TimeUnit.SECONDS);

  @Before
  public void setUp() {
    mMockDatagramChannel = mock(DatagramChannel.class);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    mService = new UDPByteListeningService(mMockDatagramChannel, LOCAL_PORT, executor);
  }

  @Test
  public void shouldReceiveMessageAndSendToListener() throws IOException, InterruptedException {
    when(mMockDatagramChannel.receive(any())).thenAnswer(new MessageAnswer())
        .thenAnswer(new ClosedByInterruptAnswer());
    BlockingQueue<QueueingByteListener.MessageWithSender> queue = new LinkedBlockingQueue<>();
    QueueingByteListener byteListener = new QueueingByteListener(queue);
    mService.registerListener(byteListener);
    mService.start();

    QueueingByteListener.MessageWithSender msg = queue.take();
    assertArrayEquals(EXAMPLE_MESSAGE, msg.getMessage());
    assertEquals(REMOTE_ADDRESS, msg.getSender());

    mService.stop();
    mService.unregisterListener(byteListener);
  }

  @Test
  public void shouldReceiveMessageAfterReceiveException() throws IOException, InterruptedException {
    when(mMockDatagramChannel.receive(any())).thenThrow(new IOException())
        .thenAnswer(new MessageAnswer())
        .thenAnswer(new ClosedByInterruptAnswer());
    BlockingQueue<QueueingByteListener.MessageWithSender> queue = new LinkedBlockingQueue<>();
    QueueingByteListener byteListener = new QueueingByteListener(queue);
    mService.registerListener(byteListener);
    mService.start();

    QueueingByteListener.MessageWithSender msg = queue.take();
    assertArrayEquals(EXAMPLE_MESSAGE, msg.getMessage());
    assertEquals(REMOTE_ADDRESS, msg.getSender());

    mService.stop();
    mService.unregisterListener(byteListener);
  }

  @Test
  public void shouldReceiveMessageAfterUnexpectedTypeOfSenderAddress()
      throws IOException, InterruptedException {
    when(mMockDatagramChannel.receive(any())).thenAnswer(new UnexpectedSenderAnswer())
        .thenAnswer(new MessageAnswer())
        .thenAnswer(new ClosedByInterruptAnswer());
    BlockingQueue<QueueingByteListener.MessageWithSender> queue = new LinkedBlockingQueue<>();
    QueueingByteListener byteListener = new QueueingByteListener(queue);
    mService.registerListener(byteListener);
    mService.start();

    QueueingByteListener.MessageWithSender msg = queue.take();
    assertArrayEquals(EXAMPLE_MESSAGE, msg.getMessage());
    assertEquals(REMOTE_ADDRESS, msg.getSender());

    mService.stop();
    mService.unregisterListener(byteListener);
  }

  @Test
  public void shouldBindToLocalAddress() throws IOException {
    mService.start();
    PortMatcher localPortMatcher = new PortMatcher(LOCAL_PORT);
    verify(mMockDatagramChannel).bind(argThat(localPortMatcher));
    mService.stop();
  }

  @Test(expected = IOException.class)
  public void shouldThrowExceptionOnBindException() throws IOException {
    when(mMockDatagramChannel.bind(any())).thenThrow(new IOException());
    mService.start();
  }

  private static class MessageAnswer implements Answer<SocketAddress> {
    @Override
    public SocketAddress answer(InvocationOnMock invocation) throws Throwable {
      ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
      buffer.put(EXAMPLE_MESSAGE);
      return REMOTE_ADDRESS;
    }
  }

  /**
   * UDPByteListeningService expects sender's address to be of InetSocketAddress type. This answer
   * tests whether other address breaks the implementation.
   */
  private static class UnexpectedSenderAnswer implements Answer<SocketAddress> {
    @Override
    public SocketAddress answer(InvocationOnMock invocation) throws Throwable {
      ByteBuffer buffer = (ByteBuffer) invocation.getArguments()[0];
      buffer.put(EXAMPLE_MESSAGE);
      return mock(SocketAddress.class);
    }
  }

  /**
   * Datagram channel blocks on receive and on interrupt it throws ClosedByInterruptException.
   * This answer models this behaviour.
   */
  private static class ClosedByInterruptAnswer implements Answer<SocketAddress> {
    @SuppressFBWarnings({"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"})
    @Override
    public SocketAddress answer(InvocationOnMock invocation) throws Throwable {
      try {
        synchronized (this) {
          this.wait();
        }
      } catch (InterruptedException e) {
        throw new ClosedByInterruptException();
      } catch (Throwable e) {
        throw e;
      }
      throw new IllegalStateException();
    }
  }

  private static class PortMatcher extends ArgumentMatcher<SocketAddress> {
    private final int mPort;

    public PortMatcher(int port) {
      mPort = port;
    }

    @Override
    public boolean matches(Object argument) {
      if (!(argument instanceof InetSocketAddress)) {
        return false;
      }
      InetSocketAddress address = (InetSocketAddress) argument;
      return address.getPort() == mPort;
    }
  }
}
