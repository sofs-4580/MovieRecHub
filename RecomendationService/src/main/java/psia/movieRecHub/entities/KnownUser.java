package psia.movieRecHub.entities;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/** A user this service has seen, plus the profile data the Frontend pushed for them. */
@Entity
public class KnownUser {

  @Id private Long userId;

  @ElementCollection(fetch = FetchType.EAGER)
  @Fetch(FetchMode.SELECT)
  private Set<String> favoriteGenres = new HashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @Fetch(FetchMode.SELECT)
  private Set<Integer> favoriteDecades = new HashSet<>();

  /** When the profile last changed; the agent recomputes if this is newer than the cache. */
  @Column(nullable = false)
  private Instant profileUpdatedAt = Instant.now();

  protected KnownUser() {}

  public KnownUser(Long userId) {
    this.userId = userId;
  }

  /** Replaces the profile data. Returns true if something actually changed. */
  public boolean updateProfile(Set<String> genres, Set<Integer> decades) {
    if (favoriteGenres.equals(genres) && favoriteDecades.equals(decades)) {
      return false;
    }
    favoriteGenres.clear();
    favoriteGenres.addAll(genres);
    favoriteDecades.clear();
    favoriteDecades.addAll(decades);
    profileUpdatedAt = Instant.now();
    return true;
  }

  public Long getUserId() {
    return userId;
  }

  public Set<String> getFavoriteGenres() {
    return Collections.unmodifiableSet(favoriteGenres);
  }

  public Set<Integer> getFavoriteDecades() {
    return Collections.unmodifiableSet(favoriteDecades);
  }

  public Instant getProfileUpdatedAt() {
    return profileUpdatedAt;
  }
}
