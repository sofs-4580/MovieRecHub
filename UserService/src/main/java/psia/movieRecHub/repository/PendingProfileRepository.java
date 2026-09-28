package psia.movieRecHub.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PendingProfileRepository {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private static final TypeReference<Set<String>> GENRES = new TypeReference<>() {};

  public PendingProfileRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void enqueue(long userId, Set<String> favoriteGenres) {
    try {
      jdbc.update("INSERT INTO pending_profiles (user_id, favorite_genres) VALUES (?, ?::jsonb)",
          userId, mapper.writeValueAsString(favoriteGenres));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Cannot serialize initial favorite genres", exception);
    }
  }

  /** Must run in the same transaction as delivery and acknowledgement. */
  public Optional<PendingProfileCreation> lockNextDue() {
    return jdbc.query(
        "SELECT user_id, favorite_genres FROM pending_profiles "
            + "WHERE next_attempt_at <= CURRENT_TIMESTAMP "
            + "ORDER BY next_attempt_at, user_id LIMIT 1 FOR UPDATE SKIP LOCKED",
        (rs, row) -> {
          try {
            return new PendingProfileCreation(rs.getLong("user_id"),
                mapper.readValue(rs.getString("favorite_genres"), GENRES));
          } catch (JsonProcessingException exception) {
            throw new DataRetrievalFailureException("Invalid pending profile preferences", exception);
          }
        }).stream().findFirst();
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
