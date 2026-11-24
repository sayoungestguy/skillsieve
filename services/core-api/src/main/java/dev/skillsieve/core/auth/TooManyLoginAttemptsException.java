package dev.skillsieve.core.auth;

public class TooManyLoginAttemptsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TooManyLoginAttemptsException() {
    super("Too many login attempts. Wait a minute and try again.");
  }
}
