package psia.movieRecHub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username"))
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30, updatable = false)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected User() {}

  /** Accepts an already encoded password; password encoding belongs to the authentication layer. */
  public User(String username, String passwordHash) {
    if (username == null) {
      throw new IllegalArgumentException("Username is required");
    }
    String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
    if (!normalizedUsername.matches("[a-z0-9_]{3,30}")) {
      throw new IllegalArgumentException(
          "Username must contain 3 to 30 lowercase letters, digits or underscores");
    }
    this.username = normalizedUsername;
    changePasswordHash(passwordHash);
  }

  /** Replaces the encoded password; the caller must authenticate the account first. */
  public void changePasswordHash(String passwordHash) {
    if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 255) {
      throw new IllegalArgumentException("Password hash must contain 1 to 255 characters");
    }
    this.passwordHash = passwordHash;
  }

  @PrePersist
  private void initializeCreatedAt() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
