package me.gregorias.ghoul.security;

public class GhoulProtocolException extends Exception {
  private static final long serialVersionUID = 1L;

  public GhoulProtocolException() {
  }

  public GhoulProtocolException(String message) {
    super(message);
  }

  public GhoulProtocolException(Throwable cause) {
    super(cause);
  }

  public GhoulProtocolException(String message, Throwable cause) {
    super(message, cause);
  }

  public GhoulProtocolException(String message, Throwable cause, boolean enableSuppression,
                           boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
