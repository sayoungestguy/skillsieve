package dev.skillsieve.core.auth;

/** The sign-in cannot finish; {@code reason} goes to the login page as ?oidc=<reason>. */
class OidcLoginException extends RuntimeException {

  private final String reason;

  OidcLoginException(String reason, String message) {
    super(message);
    this.reason = reason;
  }

  OidcLoginException(String reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  String reason() {
    return reason;
  }
}
