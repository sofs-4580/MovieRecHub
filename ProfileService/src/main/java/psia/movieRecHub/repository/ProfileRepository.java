package psia.movieRecHub.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.model.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Profile p WHERE p.userId = :userId")
  Optional<Profile> findByUserIdForUpdate(@Param("userId") Long userId);

  /** Returns 1 when inserted, or 0 when a profile already exists; never overwrites it. */
  @Modifying
  @Transactional
  @Query(
      value = "INSERT INTO profiles (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
      nativeQuery = true)
  int insertInitialProfileIfAbsent(@Param("userId") Long userId);

  @Modifying
  @Query(value = "INSERT INTO profile_favorite_genres (user_id, genre) VALUES (:userId, :genre)",
      nativeQuery = true)
  int insertInitialFavoriteGenre(@Param("userId") Long userId, @Param("genre") String genre);
}
