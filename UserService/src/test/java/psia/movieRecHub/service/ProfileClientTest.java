package psia.movieRecHub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class ProfileClientTest {

  private HttpServer server;
  private final AtomicReference<String> request = new AtomicReference<>();

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @ParameterizedTest
  @ValueSource(ints = {201, 204})
  void acceptsCreatedAndAlreadyExistingResponses(int status) {
    respond(status);
    assertThatCode(() -> client().createInitialProfile(34L)).doesNotThrowAnyException();
    assertThat(request.get()).isEqualTo("PUT /internal/profiles/34");
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 202, 400, 404, 500, 503})
  void rejectsFailuresAndUnexpectedSuccessStatuses(int status) {
    respond(status);
    assertThatThrownBy(() -> client().createInitialProfile(34L))
        .isInstanceOf(RestClientException.class);
  }

  private void respond(int status) {
    server.createContext("/internal/profiles/34", exchange -> {
      request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    });
  }

  private ProfileClient client() {
    return new ProfileClient(RestClient.builder(),
        "http://127.0.0.1:" + server.getAddress().getPort(),
        Duration.ofSeconds(1), Duration.ofSeconds(1));
  }
}
