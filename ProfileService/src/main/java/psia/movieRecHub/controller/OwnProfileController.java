package psia.movieRecHub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.OwnProfileResponse;
import psia.movieRecHub.dto.UpdateProfileRequest;
import psia.movieRecHub.service.OwnProfileService;
import psia.movieRecHub.service.ProfileSessionClient;

@RestController
@RequestMapping("/api/profiles/me")
public class OwnProfileController {

  private final ProfileSessionClient sessions;
  private final OwnProfileService profiles;

  public OwnProfileController(ProfileSessionClient sessions, OwnProfileService profiles) {
    this.sessions = sessions;
    this.profiles = profiles;
  }

  @GetMapping
  public OwnProfileResponse getOwn(@CookieValue(name = "JSESSIONID", required = false) String sessionId) {
    return profiles.getOwn(sessions.requireUserId(sessionId));
  }

  @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public OwnProfileResponse updateOwn(
      @CookieValue(name = "JSESSIONID", required = false) String sessionId,
      @RequestBody JsonNode body) {
    long userId = sessions.requireUserId(sessionId);
    return profiles.updateOwn(userId, UpdateProfileRequest.fromJson(body));
  }
}
