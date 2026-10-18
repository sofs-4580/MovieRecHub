package psia.movieRecHub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import psia.movieRecHub.service.ProfileAuthenticationRequiredException;
import psia.movieRecHub.service.ProfileSessionClient;
import psia.movieRecHub.service.SessionVerificationUnavailableException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OwnProfileIT {

  private static final long OWNER = 45001L;
  private static final long OTHER = 45002L;

  @Autowired private TestRestTemplate http;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private ProfileSessionClient sessions;

  @BeforeEach
  void prepare() {
    jdbc.update("DELETE FROM profiles WHERE user_id IN (?, ?)", OWNER, OTHER);
    jdbc.update("INSERT INTO profiles (user_id) VALUES (?), (?)", OWNER, OTHER);
    jdbc.update("INSERT INTO profile_favorite_genres (user_id, genre) VALUES (?, 'DRAMA')", OWNER);
    jdbc.update("INSERT INTO profile_favorite_genres (user_id, genre) VALUES (?, 'HORROR')", OTHER);
    when(sessions.requireUserId("owner-session")).thenReturn(OWNER);
    when(sessions.requireUserId("other-session")).thenReturn(OTHER);
    when(sessions.requireUserId(null)).thenThrow(new ProfileAuthenticationRequiredException());
    when(sessions.requireUserId("invalid-session")).thenThrow(new ProfileAuthenticationRequiredException());
    when(sessions.requireUserId("service-down")).thenThrow(new SessionVerificationUnavailableException());
  }

  @AfterEach
  void clean() {
    jdbc.update("DELETE FROM profiles WHERE user_id IN (?, ?)", OWNER, OTHER);
  }

  @Test
  void readsOnlyTheProfileSelectedByTheAuthenticatedSession() {
    ResponseEntity<JsonNode> owner = get("owner-session");
    ResponseEntity<JsonNode> other = get("other-session");
    assertThat(owner.getStatusCode().value()).isEqualTo(200);
    assertThat(owner.getBody().path("userId").asLong()).isEqualTo(OWNER);
    assertThat(owner.getBody().path("favoriteGenres").size()).isEqualTo(1);
    assertThat(owner.getBody().path("favoriteGenres").get(0).asText()).isEqualTo("DRAMA");
    assertThat(owner.getBody().path("favoriteDecades").size()).isZero();
    assertThat(other.getBody().path("userId").asLong()).isEqualTo(OTHER);
    assertThat(other.getBody().path("favoriteGenres").get(0).asText()).isEqualTo("HORROR");
  }

  @Test
  void missingOrInvalidSessionIsUnauthorized() {
    for (String cookie : new String[] {null, "invalid-session"}) {
      ResponseEntity<JsonNode> get = get(cookie);
      ResponseEntity<JsonNode> patch = patch(cookie, "{\"bio\":\"unauthorized\"}");
      assertThat(get.getStatusCode().value()).isEqualTo(401);
      assertThat(patch.getStatusCode().value()).isEqualTo(401);
      assertThat(get.getBody().path("code").asText()).isEqualTo("UNAUTHORIZED");
    }
    assertThat(jdbc.queryForObject("SELECT bio FROM profiles WHERE user_id = ?", String.class, OWNER))
        .isEmpty();
  }

  @Test
  void userServiceFailureReturns503WithoutEditing() {
    assertThat(get("service-down").getStatusCode().value()).isEqualTo(503);
    ResponseEntity<JsonNode> response = patch("service-down", "{\"bio\":\"invalid\"}");
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody().path("code").asText()).isEqualTo("AUTH_SERVICE_UNAVAILABLE");
  }

  @Test
  void missingProfileReportsPendingCondition() {
    jdbc.update("DELETE FROM profiles WHERE user_id = ?", OWNER);
    assertThat(get("owner-session").getStatusCode().value()).isEqualTo(404);
    ResponseEntity<JsonNode> response = patch("owner-session", "{\"bio\":\"later\"}");
    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getBody().path("code").asText()).isEqualTo("PROFILE_NOT_READY");
  }

  @Test
  void partialUpdateAddsDecadesAndPreservesOmittedFields() {
    Instant createdAt = jdbc.queryForObject("SELECT created_at FROM profiles WHERE user_id = ?",
        java.sql.Timestamp.class, OWNER).toInstant();
    ResponseEntity<JsonNode> first = patch("owner-session",
        "{\"displayName\":\"Ana\",\"bio\":\"Cinema\",\"favoriteDecades\":[1980,2000]}");
    assertThat(first.getStatusCode().value()).isEqualTo(200);
    assertThat(first.getBody().path("favoriteGenres").get(0).asText()).isEqualTo("DRAMA");
    assertThat(first.getBody().path("favoriteDecades").size()).isEqualTo(2);
    ResponseEntity<JsonNode> second = patch("owner-session", "{\"favoriteGenres\":[\"COMEDY\"]}");
    assertThat(second.getStatusCode().value()).isEqualTo(200);
    assertThat(second.getBody().path("displayName").asText()).isEqualTo("Ana");
    assertThat(second.getBody().path("bio").asText()).isEqualTo("Cinema");
    assertThat(second.getBody().path("favoriteGenres").get(0).asText()).isEqualTo("COMEDY");
    assertThat(second.getBody().path("favoriteDecades").size()).isEqualTo(2);
    assertThat(jdbc.queryForList("SELECT decade FROM profile_favorite_decades WHERE user_id = ?",
        Integer.class, OWNER)).containsExactlyInAnyOrder(1980, 2000);
    assertThat(jdbc.queryForObject("SELECT created_at FROM profiles WHERE user_id = ?",
        java.sql.Timestamp.class, OWNER).toInstant()).isEqualTo(createdAt);
    assertThat(jdbc.queryForObject("SELECT genre FROM profile_favorite_genres WHERE user_id = ?",
        String.class, OWNER)).isEqualTo("COMEDY");
    assertThat(jdbc.queryForObject("SELECT bio FROM profiles WHERE user_id = ?", String.class, OTHER))
        .isEmpty();
  }

  @Test
  void nullClearsOptionalFieldsWithoutChangingGenres() {
    patch("owner-session", "{\"displayName\":\"Ana\",\"bio\":\"Cinema\",\"favoriteDecades\":[1990]}");
    ResponseEntity<JsonNode> response = patch("owner-session",
        "{\"displayName\":null,\"bio\":null,\"favoriteDecades\":null}");
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().path("displayName").asText()).isEmpty();
    assertThat(response.getBody().path("bio").asText()).isEmpty();
    assertThat(response.getBody().path("favoriteDecades").size()).isZero();
    assertThat(response.getBody().path("favoriteGenres").get(0).asText()).isEqualTo("DRAMA");
  }

  @Test
  void invalidPatchesCannotChangeProfileOrTargetAnotherUser() {
    Map<String, Object> before = jdbc.queryForMap("SELECT * FROM profiles WHERE user_id = ?", OWNER);
    for (String json : List.of("{}", "{\"userId\":" + OTHER + ",\"bio\":\"attack\"}",
        "{\"favoriteGenres\":[]}", "{\"favoriteGenres\":null}",
        "{\"favoriteGenres\":[\"WESTERN\"]}", "{\"favoriteDecades\":[1995]}",
        "{\"favoriteDecades\":[1850]}", "{\"favoriteDecades\":[\"1990\"]}",
        "{\"displayName\":\"" + "x".repeat(101) + "\"}")) {
      ResponseEntity<JsonNode> response = patch("owner-session", json);
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(response.getBody().path("code").asText()).isEqualTo("INVALID_PROFILE_UPDATE");
    }
    assertThat(jdbc.queryForMap("SELECT * FROM profiles WHERE user_id = ?", OWNER)).isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT genre FROM profile_favorite_genres WHERE user_id = ?",
        String.class, OTHER)).isEqualTo("HORROR");
  }

  private ResponseEntity<JsonNode> get(String sessionId) {
    return http.exchange("/api/profiles/me", HttpMethod.GET,
        new HttpEntity<>(headers(sessionId)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> patch(String sessionId, String json) {
    HttpHeaders headers = headers(sessionId);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange("/api/profiles/me", HttpMethod.PATCH,
        new HttpEntity<>(json, headers), JsonNode.class);
  }

  private HttpHeaders headers(String sessionId) {
    HttpHeaders headers = new HttpHeaders();
    if (sessionId != null) {
      headers.set(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId);
    }
    return headers;
  }
}
