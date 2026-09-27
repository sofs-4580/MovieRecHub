package psia.movieRecHub.controller;

import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.service.ProfileService;

@RestController
@RequestMapping("/internal/profiles")
public class InternalProfileController {

  private final ProfileService profiles;

  public InternalProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  @PutMapping("/{userId}")
  public ResponseEntity<Void> createInitialProfile(@PathVariable @Positive Long userId) {
    if (profiles.createInitialProfile(userId)) {
      return ResponseEntity.created(URI.create("/internal/profiles/" + userId)).build();
    }
    return ResponseEntity.noContent().build();
  }
}
