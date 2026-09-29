package psia.movieRecHub.controller;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.PublicProfilePageResponse;
import psia.movieRecHub.dto.PublicProfileResponse;
import psia.movieRecHub.service.ProfileSessionClient;
import psia.movieRecHub.service.PublicProfileService;

@RestController
@RequestMapping("/api/profiles")
public class PublicProfileController {

  private final ProfileSessionClient sessions;
  private final PublicProfileService profiles;

  public PublicProfileController(ProfileSessionClient sessions, PublicProfileService profiles) {
    this.sessions = sessions;
    this.profiles = profiles;
  }

  @GetMapping
  public PublicProfilePageResponse browse(
      @CookieValue(name = "JSESSIONID", required = false) String sessionId,
      @RequestParam(required = false) String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    sessions.requireUserId(sessionId);
    return profiles.browse(username, page, size);
  }

  @GetMapping("/{userId}")
  public PublicProfileResponse get(
      @CookieValue(name = "JSESSIONID", required = false) String sessionId,
      @PathVariable long userId) {
    sessions.requireUserId(sessionId);
    return profiles.get(userId);
  }
}
