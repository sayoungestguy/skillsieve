package dev.skillsieve.core.common;

/** A resource does not exist or belongs to another user (both return 404, never 403). */
public class NotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String resource) {
    super(resource + " not found");
  }
}
