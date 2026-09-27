package psia.movieRecHub.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "profiles")
public class Profile {

  // External identity supplied by UserService; no cross-service JPA association.
  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName = "";

  @Column(nullable = false, length = 2000)
  private String bio = "";

  @ElementCollection
  @CollectionTable(name = "profile_favorite_genres", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "genre", nullable = false, length = 20)
  private Set<MovieGenre> favoriteGenres = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Profile() {}

  public Profile(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }
    this.userId = userId;
  }

  /** Replaces profile details; authentication and partial updates belong to the API layer. */
  public void updateDetails(String displayName, String bio, Set<MovieGenre> favoriteGenres) {
    if (displayName == null || displayName.length() > 100) {
      throw new IllegalArgumentException("Display name must contain at most 100 characters");
    }
    if (bio == null || bio.length() > 2000) {
      throw new IllegalArgumentException("Bio must contain at most 2000 characters");
    }
    if (favoriteGenres == null || favoriteGenres.stream().anyMatch(genre -> genre == null)) {
      throw new IllegalArgumentException("Favorite genres must not be null or contain null");
    }
    Set<MovieGenre> genres = new HashSet<>(favoriteGenres);
    if (!this.displayName.equals(displayName)
        || !this.bio.equals(bio)
        || !this.favoriteGenres.equals(genres)) {
      this.displayName = displayName;
      this.bio = bio;
      this.favoriteGenres.clear();
      this.favoriteGenres.addAll(genres);
      // Marks the entity dirty even when only its element collection changes.
      this.updatedAt = Instant.now();
    }
  }

  @PrePersist
  private void initializeTimestamps() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  private void updateTimestamp() {
    updatedAt = Instant.now();
  }

  public Long getUserId() {
    return userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getBio() {
    return bio;
  }

  public Set<MovieGenre> getFavoriteGenres() {
    return Collections.unmodifiableSet(favoriteGenres);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
