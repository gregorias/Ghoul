package me.gregorias.ghoul.utils;

/**
 * Exception signalling error in deserialization process caused by incorrectly serialized message.
 */
public class DeserializationException extends Exception {
  private static final long serialVersionUID = 1L;

  public DeserializationException() {
  }

  public DeserializationException(String message) {
    super(message);
  }

  public DeserializationException(Throwable cause) {
    super(cause);
  }

  public DeserializationException(String message, Throwable cause) {
    super(message, cause);
  }

  public DeserializationException(String message, Throwable cause, boolean enableSuppression,
                           boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
