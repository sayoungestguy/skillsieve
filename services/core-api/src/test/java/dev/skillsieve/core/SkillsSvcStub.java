package dev.skillsieve.core;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Tiny fake of skills-svc POST /v1/extract built on the JDK HTTP server (no extra library). */
public final class SkillsSvcStub {

  private final HttpServer server;
  private final AtomicInteger status = new AtomicInteger(503);
  private final AtomicReference<String> body = new AtomicReference<>("{\"detail\":\"busy\"}");
  private final AtomicReference<String> lastToken = new AtomicReference<>();
  private final AtomicReference<String> lastRequest = new AtomicReference<>();
  private final AtomicInteger calls = new AtomicInteger();

  private SkillsSvcStub(HttpServer server) {
    this.server = server;
    server.createContext(
        "/v1/extract",
        exchange -> {
          calls.incrementAndGet();
          lastToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
          lastRequest.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status.get(), bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  public static SkillsSvcStub start() {
    try {
      return new SkillsSvcStub(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public void respond(int statusCode, String json) {
    status.set(statusCode);
    body.set(json);
  }

  public String lastToken() {
    return lastToken.get();
  }

  public String lastRequest() {
    return lastRequest.get();
  }

  public int calls() {
    return calls.get();
  }
}
