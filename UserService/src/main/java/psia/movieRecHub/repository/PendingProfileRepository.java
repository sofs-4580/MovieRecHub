package psia.movieRecHub.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PendingProfileRepository {

  private final JdbcTemplate jdbc;

  public PendingProfileRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void enqueue(long userId) {
    jdbc.update("INSERT INTO pending_profiles (user_id) VALUES (?)", userId);
  }

  /** Must run in the same transaction as delivery and acknowledgement. */
  public Optional<Long> lockNextDue() {
    return jdbc.queryForList(
        "SELECT user_id FROM pending_profiles WHERE next_attempt_at <= CURRENT_TIMESTAMP "
            + "ORDER BY next_attempt_at, user_id LIMIT 1 FOR UPDATE SKIP LOCKED",
        Long.class).stream().findFirst();
  }

  public void complete(long userId) {
    jdbc.update("DELETE FROM pending_profiles WHERE user_id = ?", userId);
  }

  public void retryLater(long userId, long delayMillis, String error) {
    jdbc.update(
        "UPDATE pending_profiles SET attempts = attempts + 1, "
            + "next_attempt_at = clock_timestamp() + (? * INTERVAL '1 millisecond'), "
            + "last_error = ? WHERE user_id = ?",
        delayMillis, error, userId);
  }
}
