package dev.skillsieve.core.common;

/** The client sent an old {@code version}; it must reload before changing the row again. */
public class StaleVersionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StaleVersionException() {
    super("This item was changed elsewhere. Reload and try again.");
  }
}
