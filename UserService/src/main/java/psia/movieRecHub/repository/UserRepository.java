package psia.movieRecHub.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import psia.movieRecHub.model.User;

/** Persistence operations for accounts; authentication and profile management are separate concerns. */
public interface UserRepository extends JpaRepository<User, Long> {

  /** Returns the account for a canonical (trimmed, lowercase) username, or empty if absent. */
  Optional<User> findByUsername(String username);

  /**
   * Checks a canonical username before registration. The database unique constraint remains the
   * authority when concurrent registrations occur.
   */
  boolean existsByUsername(String username);
}
