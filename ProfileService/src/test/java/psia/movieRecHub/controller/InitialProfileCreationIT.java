package psia.movieRecHub.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import psia.movieRecHub.model.Profile;
import psia.movieRecHub.repository.ProfileRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InitialProfileCreationIT {

  private static final long USER_ID = 34001L;
  private static final long OTHER_USER_ID = 34002L;

  @Autowired private TestRestTemplate http;
  @Autowired private ProfileRepository profiles;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void removeTestProfiles() {
    jdbc.update("DELETE FROM profiles WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
  }

  @Test
  void createsDefaultProfileAndReturnsLocation() {
    Instant before = Instant.now().minusSeconds(1);
    ResponseEntity<String> response = create(Long.toString(USER_ID));

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getHeaders().getLocation().toString())
        .isEqualTo("/internal/profiles/" + USER_ID);
    assertThat(response.getBody()).isNull();
    Profile profile = profiles.findById(USER_ID).orElseThrow();
    assertThat(profile.getUserId()).isEqualTo(USER_ID);
    assertThat(profile.getDisplayName()).isEmpty();
    assertThat(profile.getBio()).isEmpty();
    assertThat(profile.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
    assertThat(profile.getUpdatedAt()).isEqualTo(profile.getCreatedAt());
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM profile_favorite_genres WHERE user_id = ?", Long.class, USER_ID))
        .isZero();
  }

  @Test
  void repeatedRequestPreservesEditedDetailsGenresAndTimestamps() {
    assertThat(create(Long.toString(USER_ID)).getStatusCode().value()).isEqualTo(201);
    jdbc.update(
        "UPDATE profiles SET display_name = ?, bio = ?, updated_at = ? WHERE user_id = ?",
        "Ana", "Cinema e documentários", java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), USER_ID);
    jdbc.update(
        "INSERT INTO profile_favorite_genres (user_id, genre) VALUES (?, 'DOCUMENTARY')", USER_ID);
    Map<String, Object> before = jdbc.queryForMap("SELECT * FROM profiles WHERE user_id = ?", USER_ID);

    for (int attempt = 0; attempt < 3; attempt++) {
      ResponseEntity<String> response = create(Long.toString(USER_ID));
      assertThat(response.getStatusCode().value()).isEqualTo(204);
      assertThat(response.getBody()).isNull();
    }

    assertThat(jdbc.queryForMap("SELECT * FROM profiles WHERE user_id = ?", USER_ID)).isEqualTo(before);
    assertThat(jdbc.queryForList(
        "SELECT genre FROM profile_favorite_genres WHERE user_id = ?", String.class, USER_ID))
        .containsExactly("DOCUMENTARY");
  }

  @Test
  void createsInitialGenresWithTheProfile() {
    ResponseEntity<String> response = createWithGenres(USER_ID, "DRAMA", "SCI_FI");

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(jdbc.queryForList(
        "SELECT genre FROM profile_favorite_genres WHERE user_id = ?", String.class, USER_ID))
        .containsExactlyInAnyOrder("DRAMA", "SCI_FI");
    assertThat(profiles.findById(USER_ID).orElseThrow().getDisplayName()).isEmpty();
  }

  @Test
  void retryWithDifferentPreferencesCannotReplaceAnExistingProfile() {
    assertThat(createWithGenres(USER_ID, "DRAMA").getStatusCode().value()).isEqualTo(201);
    jdbc.update("UPDATE profiles SET display_name = 'Edited' WHERE user_id = ?", USER_ID);
    Map<String, Object> before = jdbc.queryForMap("SELECT * FROM profiles WHERE user_id = ?", USER_ID);

    assertThat(createWithGenres(USER_ID, "HORROR").getStatusCode().value()).isEqualTo(204);
    assertThat(jdbc.queryForMap("SELECT * FROM profiles WHERE user_id = ?", USER_ID)).isEqualTo(before);
    assertThat(jdbc.queryForList(
        "SELECT genre FROM profile_favorite_genres WHERE user_id = ?", String.class, USER_ID))
        .containsExactly("DRAMA");
  }

  @Test
  void rejectsInvalidPreferenceBodiesWithoutCreatingAnEmptyProfile() {
    for (Object body : List.of(
        Map.of(),
        Map.of("favoriteGenres", List.of()),
        Map.of("favoriteGenres", List.of("WESTERN")),
        Map.of("favoriteGenres", List.of("DRAMA", "NO_SUCH_GENRE")))) {
      ResponseEntity<String> response = http.exchange("/internal/profiles/" + USER_ID,
          HttpMethod.PUT, new HttpEntity<>(body), String.class);
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(response.getBody()).contains("INVALID_INITIAL_PROFILE");
      assertThat(profiles.existsById(USER_ID)).isFalse();
    }
  }

  @Test
  void createsIndependentProfilesForDifferentUsers() {
    assertThat(create(Long.toString(USER_ID)).getStatusCode().value()).isEqualTo(201);
    assertThat(create(Long.toString(OTHER_USER_ID)).getStatusCode().value()).isEqualTo(201);
    assertThat(profiles.existsById(USER_ID)).isTrue();
    assertThat(profiles.existsById(OTHER_USER_ID)).isTrue();
  }

  @Test
  void simultaneousRequestsCreateExactlyOneProfile() throws Exception {
    int requests = 6;
    ExecutorService executor = Executors.newFixedThreadPool(requests);
    CountDownLatch ready = new CountDownLatch(requests);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> responses = new ArrayList<>();
    try {
      for (int i = 0; i < requests; i++) {
        responses.add(executor.submit(() -> {
          ready.countDown();
          if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent requests");
          }
          return createWithGenres(USER_ID, "DRAMA", "SCI_FI").getStatusCode().value();
        }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> response : responses) {
        statuses.add(response.get(20, TimeUnit.SECONDS));
      }
      assertThat(statuses).containsOnly(201, 204);
      assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
      assertThat(statuses.stream().filter(status -> status == 204).count()).isEqualTo(requests - 1);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM profiles WHERE user_id = ?", Long.class, USER_ID)).isEqualTo(1);
      assertThat(jdbc.queryForList(
          "SELECT genre FROM profile_favorite_genres WHERE user_id = ?", String.class, USER_ID))
          .containsExactlyInAnyOrder("DRAMA", "SCI_FI");
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
  void rejectsInvalidUserIdsWithoutChangingDatabase(String invalidUserId) {
    long countBefore = profiles.count();
    ResponseEntity<String> response = create(invalidUserId);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).contains("INVALID_USER_ID");
    assertThat(profiles.count()).isEqualTo(countBefore);
  }

  private ResponseEntity<String> createWithGenres(long userId, String... genres) {
    return http.exchange("/internal/profiles/" + userId, HttpMethod.PUT,
        new HttpEntity<>(Map.of("favoriteGenres", genres)), String.class);
  }

  private ResponseEntity<String> create(String userId) {
    return http.exchange(
        "/internal/profiles/" + userId, HttpMethod.PUT, HttpEntity.EMPTY, String.class);
  }
}
