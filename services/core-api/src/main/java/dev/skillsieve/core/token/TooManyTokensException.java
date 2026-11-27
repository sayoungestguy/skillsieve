package dev.skillsieve.core.token;

public class TooManyTokensException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TooManyTokensException() {
    super("too many active tokens");
  }
}
