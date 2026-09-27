package psia.movieRecHub.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
  /** Serializes password changes so that the old password cannot authorize two concurrent changes. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForPasswordChange(@Param("id") Long id);
}
