package psia.movieRecHub.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Set;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.InitialProfileRequest;
import psia.movieRecHub.service.ProfileService;

@RestController
@RequestMapping("/internal/profiles")
public class InternalProfileController {

  private final ProfileService profiles;

  public InternalProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  @PutMapping("/{userId}")
  public ResponseEntity<Void> createInitialProfile(
      @PathVariable @Positive Long userId,
      @Valid @RequestBody(required = false) InitialProfileRequest request) {
    // Legacy pending jobs may have no genres; new delivery still supplies their username.
    if (request != null && request.favoriteGenres().isEmpty() && request.username() == null) {
      throw new IllegalArgumentException("Initial genres are required without a username");
    }
    if (profiles.createInitialProfile(userId, request == null ? null : request.username(),
        request == null ? Set.of() : request.favoriteGenres())) {
      return ResponseEntity.created(URI.create("/internal/profiles/" + userId)).build();
    }
    return ResponseEntity.noContent().build();
  }
}
