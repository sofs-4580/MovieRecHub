package psia.movieRecHub.service;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.model.MovieGenre;
import psia.movieRecHub.repository.ProfileRepository;

@Service
public class ProfileService {

  private final ProfileRepository profiles;

  public ProfileService(ProfileRepository profiles) {
    this.profiles = profiles;
  }

  /** Ensures a profile exists without modifying one that was previously created or edited. */
  @Transactional
  public boolean createInitialProfile(Long userId, String username, Set<MovieGenre> favoriteGenres) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }
    if (favoriteGenres == null || favoriteGenres.stream().anyMatch(genre -> genre == null)) {
      throw new IllegalArgumentException("Favorite genres must not be null or contain null");
    }
    if (username != null && !username.matches("[a-z0-9_]{3,30}")) {
      throw new IllegalArgumentException("Invalid username");
    }
    if (profiles.insertInitialProfileIfAbsent(userId, username) == 0) {
      if (username != null) {
        profiles.updateMissingUsername(userId, username);
      }
      return false;
    }
    for (MovieGenre genre : favoriteGenres) {
      profiles.insertInitialFavoriteGenre(userId, genre.name());
    }
    return true;
  }
}
