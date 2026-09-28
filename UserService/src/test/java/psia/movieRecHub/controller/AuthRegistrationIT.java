package psia.movieRecHub.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import psia.movieRecHub.dto.ApiErrorResponse;
import psia.movieRecHub.dto.RegisterResponse;
import psia.movieRecHub.model.User;
import psia.movieRecHub.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthRegistrationIT {

  @Autowired private TestRestTemplate client;

  @Autowired private UserRepository users;

  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void registrationCreatesAccountWithEncodedPasswordAndPublicResponse() {
    String password = "  Senha-de-teste-ç  ";
    String username = uniqueUsername("ana_reg_");
    String expectedUsername = username.toLowerCase();

    ResponseEntity<RegisterResponse> response =
        client.postForEntity(
            "/api/auth/register",
            Map.of("username", "  " + username.toUpperCase() + "  ", "password", password, "favoriteGenres", List.of("DRAMA", "SCI_FI")),
            RegisterResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().userId()).isPositive();
    assertThat(response.getBody().username()).isEqualTo(expectedUsername);
    User saved = users.findByUsername(expectedUsername).orElseThrow();
    assertThat(saved.getId()).isEqualTo(response.getBody().userId());
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM pending_profiles WHERE user_id = ?", Long.class, saved.getId()))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT favorite_genres::text FROM pending_profiles WHERE user_id = ?",
        String.class, saved.getId())).contains("DRAMA", "SCI_FI");
    assertThat(saved.getPasswordHash()).isNotEqualTo(password);
    assertThat(saved.getPasswordHash()).startsWith("{pbkdf2@SpringSecurity_v5_8}");
    assertThat(passwordEncoder.matches(password, saved.getPasswordHash())).isTrue();

    ResponseEntity<String> serialized =
        client.postForEntity(
            "/api/auth/register",
            Map.of("username", uniqueUsername("ana_reg_"), "password", password, "favoriteGenres", List.of("DRAMA", "SCI_FI")),
            String.class);
    assertThat(serialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(serialized.getBody()).doesNotContain(password, "passwordHash", "accessToken");
  }

  @Test
  void duplicateCanonicalUsernameReturnsConflictAndDoesNotCreateSecondAccount() {
    String password = "senha-de-teste-123";
    String username = uniqueUsername("dup_user_");
    long before = users.count();
    ResponseEntity<RegisterResponse> first =
        client.postForEntity(
            "/api/auth/register",
            Map.of("username", username, "password", password, "favoriteGenres", List.of("DRAMA", "SCI_FI")),
            RegisterResponse.class);

    ResponseEntity<ApiErrorResponse> second =
        client.postForEntity(
            "/api/auth/register",
            Map.of("username", "  " + username.toUpperCase() + "  ", "password", password, "favoriteGenres", List.of("DRAMA", "SCI_FI")),
            ApiErrorResponse.class);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(second.getBody()).isNotNull();
    assertThat(second.getBody().code()).isEqualTo("USERNAME_TAKEN");
    assertThat(users.count()).isEqualTo(before + 1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM pending_profiles WHERE user_id = ?", Long.class, first.getBody().userId()))
        .isEqualTo(1);
  }

  @Test
  void invalidCredentialsReturnBadRequestWithoutPersisting() {
    long before = users.count();
    ResponseEntity<ApiErrorResponse> shortPassword =
        client.postForEntity(
            "/api/auth/register",
            Map.of("username", "invalid_user", "password", "short", "favoriteGenres", List.of("DRAMA")),
            ApiErrorResponse.class);
    ResponseEntity<ApiErrorResponse> badUsername =
        client.postForEntity(
            "/api/auth/register",
            Map.of("username", "bad-name", "password", "valid-password", "favoriteGenres", List.of("DRAMA")),
            ApiErrorResponse.class);
    ResponseEntity<ApiErrorResponse> missingPassword =
        client.postForEntity(
            "/api/auth/register",
            Map.of("username", "no_password", "favoriteGenres", List.of("DRAMA")),
            ApiErrorResponse.class);

    for (ResponseEntity<ApiErrorResponse> response :
        List.of(shortPassword, badUsername, missingPassword)) {
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }
    assertThat(users.count()).isEqualTo(before);
  }

  @Test
  void registrationRequiresAtLeastOneSupportedFavoriteGenre() {
    long before = users.count();
    String username = uniqueUsername("genres_");
    for (Object request : List.of(
        Map.of("username", username, "password", "valid-password-123"),
        Map.of("username", username, "password", "valid-password-123", "favoriteGenres", List.of()),
        Map.of("username", username, "password", "valid-password-123", "favoriteGenres", List.of("WESTERN")),
        Map.of("username", username, "password", "valid-password-123", "favoriteGenres", List.of("drama")))) {
      ResponseEntity<ApiErrorResponse> response =
          client.postForEntity("/api/auth/register", request, ApiErrorResponse.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }
    assertThat(users.count()).isEqualTo(before);
  }

  @Test
  void malformedJsonReturnsBadRequestWithoutEchoingCredentials() {
    long before = users.count();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> malformed =
        new HttpEntity<>("{\"username\":\"broken_user\",\"password\":", headers);

    ResponseEntity<ApiErrorResponse> response =
        client.postForEntity("/api/auth/register", malformed, ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getBody().message()).doesNotContain("broken_user", "password");
    assertThat(users.count()).isEqualTo(before);
  }
  private static String uniqueUsername(String prefix) {
    return prefix + UUID.randomUUID().toString().substring(0, 8);
  }
}
