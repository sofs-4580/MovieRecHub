package psia.movieRecHub.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import psia.movieRecHub.dto.AuthenticatedSessionResponse;
import psia.movieRecHub.repository.UserRepository;
import psia.movieRecHub.service.SessionRequiredException;
import psia.movieRecHub.service.SessionService;

@RestController
public class InternalSessionController {

  private final SessionService sessions;
  private final UserRepository users;

  public InternalSessionController(SessionService sessions, UserRepository users) {
    this.sessions = sessions;
    this.users = users;
  }

  @GetMapping("/internal/auth/session")
  public AuthenticatedSessionResponse currentSession(HttpServletRequest request) {
    long userId = sessions.requireUserId(request);
    if (!users.existsById(userId)) {
      throw new SessionRequiredException();
    }
    return new AuthenticatedSessionResponse(userId);
  }
}
