package psia.movieRecHub.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.model.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

  Page<Profile> findByUsernameIsNotNull(Pageable pageable);

  @Query("SELECT p FROM Profile p WHERE p.username IS NOT NULL AND LOCATE(:username, p.username) > 0")
  Page<Profile> searchUsername(@Param("username") String username, Pageable pageable);

  @Query("SELECT p.userId FROM Profile p WHERE p.username IS NULL AND p.userId > :afterId ORDER BY p.userId")
  List<Long> findMissingUsernamesAfter(@Param("afterId") long afterId, Pageable pageable);

  @Modifying
  @Transactional
  @Query(value = "UPDATE profiles SET username = :username WHERE user_id = :userId AND username IS NULL",
      nativeQuery = true)
  int updateMissingUsername(@Param("userId") long userId, @Param("username") String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Profile p WHERE p.userId = :userId")
  Optional<Profile> findByUserIdForUpdate(@Param("userId") Long userId);

  /** Returns 1 when inserted, or 0 when a profile already exists; never overwrites it. */
  @Modifying
  @Transactional
  @Query(
      value = "INSERT INTO profiles (user_id, username) VALUES (:userId, :username) "
          + "ON CONFLICT (user_id) DO NOTHING",
      nativeQuery = true)
  int insertInitialProfileIfAbsent(@Param("userId") Long userId, @Param("username") String username);

  @Modifying
  @Query(value = "INSERT INTO profile_favorite_genres (user_id, genre) VALUES (:userId, :genre)",
      nativeQuery = true)
  int insertInitialFavoriteGenre(@Param("userId") Long userId, @Param("genre") String genre);
}
