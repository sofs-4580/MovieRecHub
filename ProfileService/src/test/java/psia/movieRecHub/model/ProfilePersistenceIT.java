package psia.movieRecHub.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProfilePersistenceIT {

  @Autowired private EntityManager entityManager;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void persistsDefaultProfileWithExternalUserIdAndTimestamps() {
    Instant before = Instant.now().minusSeconds(1);
    Profile profile = new Profile(34L);
    entityManager.persist(profile);
    entityManager.flush();
    entityManager.clear();

    Profile loaded = entityManager.find(Profile.class, 34L);
    assertThat(loaded.getUserId()).isEqualTo(34L);
    assertThat(loaded.getDisplayName()).isEmpty();
    assertThat(loaded.getBio()).isEmpty();
    assertThat(loaded.getFavoriteGenres()).isEmpty();
    assertThat(loaded.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
    assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
  }

  @Test
  void persistsDetailsAndGenresAsNames() {
    Profile profile = new Profile(34L);
    profile.updateDetails("Ana", "Gosto de cinema.", Set.of(MovieGenre.DRAMA, MovieGenre.SCI_FI));
    entityManager.persist(profile);
    entityManager.flush();
    entityManager.clear();

    Profile loaded = entityManager.find(Profile.class, 34L);
    assertThat(loaded.getDisplayName()).isEqualTo("Ana");
    assertThat(loaded.getBio()).isEqualTo("Gosto de cinema.");
    assertThat(loaded.getFavoriteGenres()).containsExactlyInAnyOrder(MovieGenre.DRAMA, MovieGenre.SCI_FI);
    assertThat(jdbc.queryForList("SELECT genre FROM profile_favorite_genres", String.class))
        .containsExactlyInAnyOrder("DRAMA", "SCI_FI");
  }

  @Test
  void updatesTimestampWhenOnlyGenresChangeAndPreservesCreationTime() {
    jdbc.update("INSERT INTO profiles (user_id, created_at, updated_at) VALUES (34, '2020-01-01T00:00:00Z', '2020-01-01T00:00:00Z')");
    Profile profile = entityManager.find(Profile.class, 34L);
    Instant createdAt = profile.getCreatedAt();
    profile.updateDetails("", "", Set.of(MovieGenre.COMEDY));
    entityManager.flush();
    entityManager.clear();

    Profile loaded = entityManager.find(Profile.class, 34L);
    assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
    assertThat(loaded.getUpdatedAt()).isAfter(createdAt);
    assertThat(loaded.getFavoriteGenres()).containsExactly(MovieGenre.COMEDY);
    loaded.updateDetails("", "", Set.of());
    entityManager.flush();
    entityManager.clear();
    assertThat(entityManager.find(Profile.class, 34L).getFavoriteGenres()).isEmpty();
  }

  @Test
  void migrationSuppliesDefaultsAndIsRecordedOnce() {
    jdbc.update("INSERT INTO profiles (user_id) VALUES (34)");
    Profile loaded = entityManager.find(Profile.class, 34L);
    assertThat(loaded.getDisplayName()).isEmpty();
    assertThat(loaded.getBio()).isEmpty();
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getUpdatedAt()).isNotNull();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void databaseRejectsTwoProfilesForTheSameUser() {
    jdbc.update("INSERT INTO profiles (user_id) VALUES (34)");
    assertThatThrownBy(() -> jdbc.update("INSERT INTO profiles (user_id) VALUES (34)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsNonPositiveUserId() {
    assertThatThrownBy(() -> jdbc.update("INSERT INTO profiles (user_id) VALUES (0)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsGenreWithoutProfile() {
    assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO profile_favorite_genres (user_id, genre) VALUES (34, 'DRAMA')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsUnknownGenre() {
    jdbc.update("INSERT INTO profiles (user_id) VALUES (34)");
    assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO profile_favorite_genres (user_id, genre) VALUES (34, 'UNKNOWN')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsInvalidDecade() {
    jdbc.update("INSERT INTO profiles (user_id) VALUES (34)");
    assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO profile_favorite_decades (user_id, decade) VALUES (34, 1995)"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsDuplicateFavoriteGenre() {
    jdbc.update("INSERT INTO profiles (user_id) VALUES (34)");
    jdbc.update("INSERT INTO profile_favorite_genres (user_id, genre) VALUES (34, 'DRAMA')");
    assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO profile_favorite_genres (user_id, genre) VALUES (34, 'DRAMA')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingProfileAlsoDeletesItsFavoriteGenres() {
    jdbc.update("INSERT INTO profiles (user_id) VALUES (34)");
    jdbc.update("INSERT INTO profile_favorite_genres (user_id, genre) VALUES (34, 'DRAMA')");
    jdbc.update("INSERT INTO profile_favorite_decades (user_id, decade) VALUES (34, 1990)");
    jdbc.update("DELETE FROM profiles WHERE user_id = 34");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_favorite_genres", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_favorite_decades", Integer.class))
        .isZero();
  }
}
