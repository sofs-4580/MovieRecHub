package psia.movieRecHub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import psia.movieRecHub.model.User;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryIT {

  @Autowired private UserRepository users;

  @Autowired private EntityManager entityManager;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void persistsAndFindsUserWithGeneratedIdentityAndCreationTime() {
    Instant before = Instant.now().minusSeconds(1);
    User saved = users.saveAndFlush(new User("  Ana_34  ", "encoded-hash"));
    Long id = saved.getId();
    entityManager.clear();

    User loaded = users.findByUsername("ana_34").orElseThrow();
    assertThat(id).isPositive();
    assertThat(loaded.getId()).isEqualTo(id);
    assertThat(loaded.getPasswordHash()).isEqualTo("encoded-hash");
    assertThat(loaded.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
    assertThat(users.existsByUsername("ana_34")).isTrue();
    assertThat(users.findById(id)).isPresent();
  }

  @Test
  void reportsMissingUser() {
    assertThat(users.findByUsername("missing_user")).isEmpty();
    assertThat(users.existsByUsername("missing_user")).isFalse();
  }

  @Test
  void databaseRejectsDuplicateCanonicalUsername() {
    users.saveAndFlush(new User("Ana_34", "first-hash"));

    assertThatThrownBy(() -> users.saveAndFlush(new User(" ANA_34 ", "second-hash")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsNonCanonicalUsernameEvenWhenBypassingEntity() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                    "Ana_34",
                    "encoded-hash"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsMissingHashEvenWhenBypassingEntity() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users (username, password_hash) VALUES (?, ?)", "ana_34", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseSuppliesCreationTimeForDirectInserts() {
    jdbc.update(
        "INSERT INTO users (username, password_hash) VALUES (?, ?)", "ana_34", "encoded-hash");

    assertThat(users.findByUsername("ana_34").orElseThrow().getCreatedAt()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class))
        .isEqualTo(1);
  }
}
