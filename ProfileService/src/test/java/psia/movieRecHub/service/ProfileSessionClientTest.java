package psia.movieRecHub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ProfileSessionClientTest {

  private HttpServer server;
  private final AtomicReference<String> cookie = new AtomicReference<>();

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void forwardsOnlySessionCookieAndReadsAuthenticatedId() {
    respond(200, "{\"userId\":34}");
    assertThat(client().requireUserId("session-34")).isEqualTo(34L);
    assertThat(cookie.get()).isEqualTo("JSESSIONID=session-34");
  }

  @Test
  void missingSessionDoesNotContactUserService() {
    assertThatThrownBy(() -> client().requireUserId(null))
        .isInstanceOf(ProfileAuthenticationRequiredException.class);
  }

  @Test
  void rejectedSessionReturnsAuthenticationError() {
    respond(401, "{}");
    assertThatThrownBy(() -> client().requireUserId("invalid"))
        .isInstanceOf(ProfileAuthenticationRequiredException.class);
  }

  @Test
  void remoteFailureIsNotMisreportedAsInvalidLogin() {
    respond(503, "{}");
    assertThatThrownBy(() -> client().requireUserId("session-34"))
        .isInstanceOf(SessionVerificationUnavailableException.class);
  }

  private void respond(int status, String body) {
    server.createContext("/internal/auth/session", exchange -> {
      cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
  }

  private ProfileSessionClient client() {
    return new ProfileSessionClient(RestClient.builder(),
        "http://127.0.0.1:" + server.getAddress().getPort(),
        Duration.ofSeconds(1), Duration.ofSeconds(1));
  }
}
