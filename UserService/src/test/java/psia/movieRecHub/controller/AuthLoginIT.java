package psia.movieRecHub.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import psia.movieRecHub.dto.ApiErrorResponse;
import psia.movieRecHub.dto.RegisterResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthLoginIT {

  @Autowired private TestRestTemplate client;

  @Test
  void correctCredentialsReturnSessionCookieAndOnlyPublicAccountIdentifiers() {
    String username = uniqueUsername();
    String password = "senha-longa-ç-123";
    RegisterResponse account = register(username, password);
    ResponseEntity<JsonNode> response = client.postForEntity("/api/auth/login",
        Map.of("username", "  " + username.toUpperCase() + "  ", "password", password), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = response.getBody();
    assertThat(body.size()).isEqualTo(2);
    assertThat(body.path("userId").asLong()).isEqualTo(account.userId());
    assertThat(body.path("username").asText()).isEqualTo(username);
    assertThat(response.getHeaders().getFirst("Set-Cookie"))
        .contains("JSESSIONID=", "HttpOnly", "SameSite=Strict");
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, response.getHeaders().getFirst("Set-Cookie").split(";", 2)[0]);
    ResponseEntity<JsonNode> session = client.exchange("/internal/auth/session", HttpMethod.GET,
        new HttpEntity<>(headers), JsonNode.class);
    assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(session.getBody().size()).isEqualTo(1);
    assertThat(session.getBody().path("userId").asLong()).isEqualTo(account.userId());
  }

  @Test
  void wrongPasswordAndUnknownUsernameHaveIdenticalUnauthorizedResponseAndNoSession() {
    String username = uniqueUsername();
    register(username, "senha-correta-123");
    ResponseEntity<ApiErrorResponse> wrong = client.postForEntity("/api/auth/login",
        Map.of("username", username, "password", "senha-errada-123"), ApiErrorResponse.class);
    ResponseEntity<ApiErrorResponse> unknown = client.postForEntity("/api/auth/login",
        Map.of("username", uniqueUsername(), "password", "senha-errada-123"), ApiErrorResponse.class);
    assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(wrong.getBody()).isEqualTo(unknown.getBody());
    assertThat(wrong.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(wrong.getHeaders().getFirst("Set-Cookie")).isNull();
    assertThat(unknown.getHeaders().getFirst("Set-Cookie")).isNull();
  }

  @Test
  void invalidRequestReturnsBadRequestWithoutEchoingPassword() {
    ResponseEntity<ApiErrorResponse> response = client.postForEntity("/api/auth/login",
        Map.of("username", "invalid-name", "password", "sensitive-password"), ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().message()).doesNotContain("sensitive-password");
  }

  @Test
  void internalSessionEndpointRequiresLoginWithoutCreatingSession() {
    ResponseEntity<ApiErrorResponse> response =
        client.getForEntity("/internal/auth/session", ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
    assertThat(response.getHeaders().getFirst("Set-Cookie")).isNull();
  }

  @Test
  void jwksEndpointHasBeenRemoved() {
    assertThat(client.getForEntity("/.well-known/jwks.json", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void loginRequiresJsonAndDoesNotAllowCrossOriginPreflight() {
    HttpHeaders textHeaders = new HttpHeaders();
    textHeaders.setContentType(MediaType.TEXT_PLAIN);
    var plainResponse = client.postForEntity("/api/auth/login", new HttpEntity<>(
        "{\"username\":\"ana_34\",\"password\":\"test-password\"}", textHeaders), String.class);
    assertThat(plainResponse.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(plainResponse.getHeaders().getFirst("Set-Cookie")).isNull();

    HttpHeaders corsHeaders = new HttpHeaders();
    corsHeaders.setOrigin("https://other-site.example");
    corsHeaders.set("Access-Control-Request-Headers", "content-type");
    for (String path : new String[] {"/api/auth/login", "/api/users/me/password"}) {
      corsHeaders.set("Access-Control-Request-Method", path.endsWith("login") ? "POST" : "PATCH");
      var preflight = client.exchange(path, HttpMethod.OPTIONS,
          new HttpEntity<>(null, corsHeaders), String.class);
      assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(preflight.getHeaders().getAccessControlAllowOrigin()).isNull();
    }
  }

  private RegisterResponse register(String username, String password) {
    var response = client.postForEntity("/api/auth/register",
        Map.of("username", username, "password", password, "favoriteGenres", List.of("DRAMA")), RegisterResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  private static String uniqueUsername() {
    return "login_" + UUID.randomUUID().toString().substring(0, 8);
  }
}
