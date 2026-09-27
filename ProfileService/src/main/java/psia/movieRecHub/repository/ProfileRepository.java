package psia.movieRecHub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.model.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

  /** Returns 1 when inserted, or 0 when a profile already exists; never overwrites it. */
  @Modifying
  @Transactional
  @Query(
      value = "INSERT INTO profiles (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
      nativeQuery = true)
  int insertInitialProfileIfAbsent(@Param("userId") Long userId);
}
