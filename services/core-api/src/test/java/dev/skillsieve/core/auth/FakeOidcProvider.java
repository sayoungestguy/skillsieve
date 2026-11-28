package dev.skillsieve.core.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Minimal OIDC provider for tests: token + JWKS endpoints, ID tokens signed with a test RSA key.
 */
final class FakeOidcProvider {

  static final String ISSUER = "http://idp.test/auth/realms/skillsieve";
  static final String CLIENT_ID = "skillsieve-web";

  private final HttpServer server;
  private final RSAKey key;
  private final AtomicReference<Consumer<JWTClaimsSet.Builder>> customise =
      new AtomicReference<>(b -> {});
  private final AtomicReference<String> lastTokenRequest = new AtomicReference<>();
  private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
  private volatile String subject = "kc-sub-1";
  private volatile String email = "friend@test.local";
  private volatile boolean emailVerified = true;
  private volatile String nonce = "";

  private FakeOidcProvider(HttpServer server) throws JOSEException {
    this.server = server;
    this.key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    server.createContext(
        "/protocol/openid-connect/certs",
        ex -> send(ex, 200, new JWKSet(key.toPublicJWK()).toString()));
    server.createContext(
        "/protocol/openid-connect/token",
        ex -> {
          lastTokenRequest.set(
              new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          lastAuthHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
          send(
              ex,
              200,
              "{\"access_token\":\"at\",\"token_type\":\"Bearer\",\"id_token\":\""
                  + idToken()
                  + "\"}");
        });
    server.start();
  }

  static FakeOidcProvider start() {
    try {
      return new FakeOidcProvider(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  void user(String subject, String email, boolean verified) {
    this.subject = subject;
    this.email = email;
    this.emailVerified = verified;
    this.customise.set(b -> {});
  }

  void nonce(String nonce) {
    this.nonce = nonce;
  }

  void customise(Consumer<JWTClaimsSet.Builder> c) {
    customise.set(c);
  }

  String lastTokenRequest() {
    return lastTokenRequest.get();
  }

  String lastAuthHeader() {
    return lastAuthHeader.get();
  }

  private String idToken() {
    Instant now = Instant.now();
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(subject)
            .audience(CLIENT_ID)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("nonce", nonce)
            .claim("email", email)
            .claim("email_verified", emailVerified);
    customise.get().accept(claims);
    try {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims.build());
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void send(HttpExchange ex, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }
}
