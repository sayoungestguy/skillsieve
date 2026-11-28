package dev.skillsieve.core.common;

import dev.skillsieve.core.auth.InvalidCredentialsException;
import dev.skillsieve.core.auth.TooManyLoginAttemptsException;
import dev.skillsieve.core.job.DuplicateJobException;
import dev.skillsieve.core.token.TooManyTokensException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps domain exceptions to RFC 7807 Problem Details (TECHNICAL_DESIGN §5). */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
  }

  @ExceptionHandler(TooManyTokensException.class)
  ProblemDetail tooManyTokens(TooManyTokensException e) {
    return problem(
        HttpStatus.CONFLICT, "Too many tokens", "Revoke a token before creating another (max 10).");
  }

  @ExceptionHandler(DuplicateJobException.class)
  ProblemDetail duplicate(DuplicateJobException e) {
    ProblemDetail p = problem(HttpStatus.CONFLICT, "Duplicate", "You already saved this job post.");
    p.setProperty("existingId", e.existingId());
    return p;
  }

  @ExceptionHandler(StaleVersionException.class)
  ProblemDetail stale(StaleVersionException e) {
    return problem(HttpStatus.CONFLICT, "Stale version", e.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail badLogin(InvalidCredentialsException e) {
    return problem(HttpStatus.UNAUTHORIZED, "Login failed", e.getMessage());
  }

  @ExceptionHandler(TooManyLoginAttemptsException.class)
  ProblemDetail tooMany(TooManyLoginAttemptsException e) {
    return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", e.getMessage());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ProblemDetail constraint(ConstraintViolationException e) {
    ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed", null);
    p.setProperty(
        "errors",
        e.getConstraintViolations().stream()
            .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
            .toList());
    return p;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of(
                        "field",
                        f.getField(),
                        "message",
                        f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage()))
            .toList();
    ProblemDetail p =
        problem(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Validation failed",
            errors.isEmpty() ? null : errors.get(0).get("message"));
    p.setProperty("errors", errors);
    return ResponseEntity.unprocessableEntity().body(p);
  }

  private static ProblemDetail problem(HttpStatus status, String title, @Nullable String detail) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(title);
    return p;
  }
}
