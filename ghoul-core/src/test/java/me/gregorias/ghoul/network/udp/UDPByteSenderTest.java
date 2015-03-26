package me.gregorias.ghoul.network.udp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import me.gregorias.ghoul.utils.Utils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressFBWarnings("UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
public final class UDPByteSenderTest {
  private static final InetSocketAddress EXAMPLE_ADDRESS = new InetSocketAddress(1024);
  private static final byte[] EXAMPLE_MESSAGE = {0, 1, 2, 3};
  private static final MessageMatcher MESSAGE_MATCHER = new MessageMatcher();
  private UDPByteSender mUdpByteSender;
  private DatagramChannel mMockDatagramChannel;

  @Before
  public void setUp() {
    mMockDatagramChannel = mock(DatagramChannel.class);
    mUdpByteSender = new UDPByteSender(mMockDatagramChannel);
  }

  @Test
  public void shouldSendMessage() throws IOException {
    mUdpByteSender.sendMessage(EXAMPLE_ADDRESS, EXAMPLE_MESSAGE);

    verify(mMockDatagramChannel).send(argThat(MESSAGE_MATCHER), eq(EXAMPLE_ADDRESS));
  }

  @Test
  public void shouldHideException() throws IOException {
    when(mMockDatagramChannel.send(any(), any())).thenThrow(new IOException());
    mUdpByteSender.sendMessage(EXAMPLE_ADDRESS, EXAMPLE_MESSAGE);
  }

  private static class MessageMatcher extends ArgumentMatcher<ByteBuffer> {
    @Override
    public boolean matches(Object argument) {
      if (! (argument instanceof ByteBuffer)) {
        return false;
      }
      ByteBuffer buffer = (ByteBuffer) argument;
      buffer = buffer.duplicate();
      return Arrays.equals(Utils.byteBufferToArray(buffer), EXAMPLE_MESSAGE);
    }
  }
}
