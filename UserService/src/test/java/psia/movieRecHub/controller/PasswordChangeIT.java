package psia.movieRecHub.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import psia.movieRecHub.dto.LoginResponse;
import psia.movieRecHub.dto.RegisterResponse;
import psia.movieRecHub.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PasswordChangeIT {

  private static final String OLD_PASSWORD = "old-password-123";
  private static final String NEW_PASSWORD = "new-password-456";
  private final HttpClient http = HttpClient.newHttpClient();
  @LocalServerPort private int port;
  @Autowired private TestRestTemplate client;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwords;

  @Test
  void changesOnlyOwnPasswordAndNewPasswordWorksAtLogin() throws Exception {
    Account owner = account();
    Account other = account();
    String oldHash = users.findById(owner.id()).orElseThrow().getPasswordHash();
    String otherHash = users.findById(other.id()).orElseThrow().getPasswordHash();
    String newPassword = "  " + "ç".repeat(124) + "  ";
    // A client-supplied userId/username must never select the account being changed.
    HttpResponse<String> response = patch(owner.cookie(), mapper.writeValueAsString(Map.of(
        "currentPassword", OLD_PASSWORD, "newPassword", newPassword,
        "userId", other.id(), "username", other.username())));

    assertThat(response.statusCode()).isEqualTo(204);
    assertThat(response.body()).isEmpty();
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    String newHash = users.findById(owner.id()).orElseThrow().getPasswordHash();
    assertThat(newHash).isNotEqualTo(oldHash).isNotEqualTo(newPassword);
    assertThat(passwords.matches(newPassword, newHash)).isTrue();
    assertThat(users.findById(other.id()).orElseThrow().getPasswordHash()).isEqualTo(otherHash);
    assertThat(loginStatus(owner.username(), OLD_PASSWORD)).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(loginStatus(owner.username(), newPassword)).isEqualTo(HttpStatus.OK);
    assertThat(loginStatus(other.username(), OLD_PASSWORD)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void wrongCurrentPasswordDoesNotChangeStoredHash() throws Exception {
    Account owner = account();
    String hash = users.findById(owner.id()).orElseThrow().getPasswordHash();
    assertError(patch(owner.cookie(), body("incorrect-password", NEW_PASSWORD)),
        403, "INVALID_CURRENT_PASSWORD");
    assertThat(users.findById(owner.id()).orElseThrow().getPasswordHash()).isEqualTo(hash);
  }

  @Test
  void validatesPasswordsAndMalformedJsonWithoutChangingHash() throws Exception {
    Account owner = account();
    String hash = users.findById(owner.id()).orElseThrow().getPasswordHash();
    for (String json : List.of(body(OLD_PASSWORD, "short"), body(OLD_PASSWORD, "x".repeat(129)),
        body(OLD_PASSWORD, " ".repeat(12)), body("", NEW_PASSWORD),
        "{\"currentPassword\":\"old-password-123\"}",
        "{\"currentPassword\":null,\"newPassword\":\"new-password-456\"}")) {
      assertError(patch(owner.cookie(), json), 400, "VALIDATION_ERROR");
    }
    assertError(patch(owner.cookie(), "{\"currentPassword\":"), 400, "INVALID_REQUEST");
    assertThat(users.findById(owner.id()).orElseThrow().getPasswordHash()).isEqualTo(hash);
  }

  @Test
  void rejectsMissingOrUnknownSessionWithoutChangingPassword() throws Exception {
    Account owner = account();
    String hash = users.findById(owner.id()).orElseThrow().getPasswordHash();
    for (String cookie : Arrays.asList(null, "JSESSIONID=unknown-session")) {
      HttpResponse<String> response = patch(cookie, body(OLD_PASSWORD, NEW_PASSWORD));
      assertError(response, 401, "UNAUTHORIZED");
      assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }
    assertThat(users.findById(owner.id()).orElseThrow().getPasswordHash()).isEqualTo(hash);
  }

  @Test
  void successfulLoginReplacesThePreviousSession() throws Exception {
    Account owner = account();
    String loginBody = mapper.writeValueAsString(Map.of("username", owner.username(), "password", OLD_PASSWORD));
    HttpResponse<String> login = http.send(HttpRequest.newBuilder(
        URI.create("http://localhost:" + port + "/api/auth/login"))
        .header("Content-Type", "application/json").header("Cookie", owner.cookie())
        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(login.statusCode()).isEqualTo(200);
    String newCookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    assertThat(newCookie).isNotEqualTo(owner.cookie());
    assertError(patch(owner.cookie(), body(OLD_PASSWORD, NEW_PASSWORD)), 401, "UNAUTHORIZED");
    assertThat(patch(newCookie, body(OLD_PASSWORD, NEW_PASSWORD)).statusCode()).isEqualTo(204);
  }

  @Test
  void deletedAccountCannotBeChangedWithExistingSession() throws Exception {
    Account owner = account();
    users.deleteById(owner.id());
    assertError(patch(owner.cookie(), body(OLD_PASSWORD, NEW_PASSWORD)), 401, "INVALID_CREDENTIALS");
  }

  @Test
  void sessionRemainsValidButOldPasswordCannotBeReused() throws Exception {
    Account owner = account();
    assertThat(patch(owner.cookie(), body(OLD_PASSWORD, NEW_PASSWORD)).statusCode()).isEqualTo(204);
    assertError(patch(owner.cookie(), body(OLD_PASSWORD, "third-password-789")),
        403, "INVALID_CURRENT_PASSWORD");
    assertThat(patch(owner.cookie(), body(NEW_PASSWORD, "third-password-789")).statusCode())
        .isEqualTo(204);
  }

  @Test
  void concurrentChangesCannotBothUseTheSameOldPassword() throws Exception {
    Account owner = account();
    var first = http.sendAsync(request(owner.cookie(), body(OLD_PASSWORD, NEW_PASSWORD)),
        HttpResponse.BodyHandlers.ofString());
    var second = http.sendAsync(request(owner.cookie(), body(OLD_PASSWORD, "other-password-789")),
        HttpResponse.BodyHandlers.ofString());
    CompletableFuture.allOf(first, second).join();
    assertThat(List.of(first.join().statusCode(), second.join().statusCode()))
        .containsExactlyInAnyOrder(204, 403);
    String hash = users.findById(owner.id()).orElseThrow().getPasswordHash();
    String winner = first.join().statusCode() == 204 ? NEW_PASSWORD : "other-password-789";
    assertThat(passwords.matches(winner, hash)).isTrue();
  }

  @Test
  void healthRemainsPublic() {
    assertThat(client.getForEntity("/actuator/health", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private Account account() {
    String username = "pw_" + UUID.randomUUID().toString().substring(0, 8);
    var registered = client.postForEntity("/api/auth/register",
        Map.of("username", username, "password", OLD_PASSWORD), RegisterResponse.class);
    assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var loggedIn = client.postForEntity("/api/auth/login",
        Map.of("username", username, "password", OLD_PASSWORD), LoginResponse.class);
    assertThat(loggedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
    return new Account(registered.getBody().userId(), username,
        loggedIn.getHeaders().getFirst("Set-Cookie").split(";", 2)[0]);
  }

  private HttpStatus loginStatus(String username, String password) {
    return HttpStatus.valueOf(client.postForEntity("/api/auth/login",
        Map.of("username", username, "password", password), String.class).getStatusCode().value());
  }

  private String body(String currentPassword, String newPassword) throws Exception {
    return mapper.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword));
  }

  private HttpRequest request(String cookie, String body) {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me/password"))
        .header("Content-Type", "application/json")
        .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
    if (cookie != null) {
      builder.header("Cookie", cookie);
    }
    return builder.build();
  }

  private HttpResponse<String> patch(String cookie, String body) throws Exception {
    return http.send(request(cookie, body), HttpResponse.BodyHandlers.ofString());
  }

  private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(mapper.readTree(response.body()).path("code").asText()).isEqualTo(code);
    assertThat(response.body()).doesNotContain(OLD_PASSWORD, NEW_PASSWORD, "passwordHash");
  }

  private record Account(Long id, String username, String cookie) {}
}
