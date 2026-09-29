package psia.movieRecHub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import psia.movieRecHub.service.ProfileAuthenticationRequiredException;
import psia.movieRecHub.service.ProfileSessionClient;
import psia.movieRecHub.service.ProfileUsernameBackfillWorker;
import psia.movieRecHub.service.ProfileUsernameClient;
import psia.movieRecHub.repository.ProfileRepository;
import psia.movieRecHub.service.SessionVerificationUnavailableException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublicProfileIT {

  private static final long FIRST = 67001L;
  private static final long SECOND = 67002L;
  private static final long LEGACY = 67003L;

  @Autowired private TestRestTemplate http;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProfileRepository profiles;
  @MockitoBean private ProfileSessionClient sessions;

  @BeforeEach
  void setup() {
    clean();
    jdbc.update("INSERT INTO profiles (user_id, username, display_name, bio) VALUES (?, ?, ?, ?)",
        FIRST, "film_fan", "Film Fan", "Drama films");
    jdbc.update("INSERT INTO profiles (user_id, username, display_name, bio) VALUES (?, ?, ?, ?)",
        SECOND, "filmxfan", "", "Other films");
    jdbc.update("INSERT INTO profiles (user_id) VALUES (?)", LEGACY);
    jdbc.update("INSERT INTO profile_favorite_genres (user_id, genre) VALUES (?, 'DRAMA')", FIRST);
    jdbc.update("INSERT INTO profile_favorite_decades (user_id, decade) VALUES (?, 1990)", FIRST);
    when(sessions.requireUserId("valid")).thenReturn(SECOND);
    when(sessions.requireUserId(null)).thenThrow(new ProfileAuthenticationRequiredException());
    when(sessions.requireUserId("invalid")).thenThrow(new ProfileAuthenticationRequiredException());
    when(sessions.requireUserId("down")).thenThrow(new SessionVerificationUnavailableException());
  }

  @AfterEach
  void clean() {
    jdbc.update("DELETE FROM profiles WHERE user_id IN (?, ?, ?)", FIRST, SECOND, LEGACY);
  }

  @Test
  void browsesNamedProfilesInStablePagesAndSearchesLiteralUnderscore() {
    ResponseEntity<JsonNode> firstPage = get("/api/profiles?size=1", "valid");
    assertThat(firstPage.getStatusCode().value()).isEqualTo(200);
    assertThat(firstPage.getBody().path("totalElements").asLong()).isEqualTo(2);
    assertThat(firstPage.getBody().path("items").size()).isEqualTo(1);
    assertThat(firstPage.getBody().path("items").get(0).path("username").asText())
        .isEqualTo("film_fan");
    assertThat(firstPage.getBody().path("items").get(0).has("bio")).isFalse();

    ResponseEntity<JsonNode> secondPage = get("/api/profiles?size=1&page=1", "valid");
    assertThat(secondPage.getBody().path("items").get(0).path("username").asText())
        .isEqualTo("filmxfan");

    ResponseEntity<JsonNode> search = get("/api/profiles?username=FILM_FAN", "valid");
    assertThat(search.getStatusCode().value()).isEqualTo(200);
    assertThat(search.getBody().path("totalElements").asLong()).isEqualTo(1);
    assertThat(search.getBody().path("items").get(0).path("userId").asLong()).isEqualTo(FIRST);
  }

  @Test
  void readsPublicDetailsAndHidesIncompleteOrMissingProfiles() {
    ResponseEntity<JsonNode> profile = get("/api/profiles/" + FIRST, "valid");
    assertThat(profile.getStatusCode().value()).isEqualTo(200);
    assertThat(profile.getBody().path("username").asText()).isEqualTo("film_fan");
    assertThat(profile.getBody().path("bio").asText()).isEqualTo("Drama films");
    assertThat(profile.getBody().path("favoriteGenres").get(0).asText()).isEqualTo("DRAMA");
    assertThat(profile.getBody().path("favoriteDecades").get(0).asInt()).isEqualTo(1990);
    assertThat(profile.getBody().has("passwordHash")).isFalse();

    for (long id : new long[] {LEGACY, 67004L}) {
      ResponseEntity<JsonNode> missing = get("/api/profiles/" + id, "valid");
      assertThat(missing.getStatusCode().value()).isEqualTo(404);
      assertThat(missing.getBody().path("code").asText()).isEqualTo("PROFILE_NOT_FOUND");
    }
  }

  @Test
  void backfillsLegacyUsernameOverUserServiceContractWithoutChangingProfile() {
    ProfileUsernameClient users = mock(ProfileUsernameClient.class);
    when(users.usernameFor(LEGACY)).thenReturn("legacy_fan");
    ProfileUsernameBackfillWorker worker = new ProfileUsernameBackfillWorker(profiles, users, 20);

    assertThat(get("/api/profiles/" + LEGACY, "valid").getStatusCode().value()).isEqualTo(404);
    worker.fillMissingUsernames();

    assertThat(get("/api/profiles?username=legacy_fan", "valid")
        .getBody().path("totalElements").asLong()).isEqualTo(1);
    assertThat(get("/api/profiles/" + LEGACY, "valid")
        .getBody().path("username").asText()).isEqualTo("legacy_fan");
    assertThat(jdbc.queryForObject("SELECT display_name FROM profiles WHERE user_id = ?",
        String.class, LEGACY)).isEmpty();
  }

  @Test
  void requiresSessionAndValidatesPageParameters() {
    for (String session : new String[] {"invalid", "down"}) {
      int expected = session.equals("invalid") ? 401 : 503;
      assertThat(get("/api/profiles", session).getStatusCode().value()).isEqualTo(expected);
      assertThat(get("/api/profiles/" + FIRST, session).getStatusCode().value())
          .isEqualTo(expected);
    }
    assertThat(get("/api/profiles", null).getStatusCode().value()).isEqualTo(401);
    for (String url : new String[] {
        "/api/profiles?page=-1", "/api/profiles?size=0", "/api/profiles?size=51",
        "/api/profiles?username=bad%20name", "/api/profiles?page=wrong",
        "/api/profiles/0", "/api/profiles/not-a-number"}) {
      assertThat(get(url, "valid").getStatusCode().value()).isEqualTo(400);
    }
  }

  private ResponseEntity<JsonNode> get(String url, String sessionId) {
    HttpHeaders headers = new HttpHeaders();
    if (sessionId != null) {
      headers.set(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId);
    }
    return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }
}
