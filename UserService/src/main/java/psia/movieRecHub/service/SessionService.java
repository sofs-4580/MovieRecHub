package psia.movieRecHub.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

  private static final String USER_ID = "authenticatedUserId";

  public void start(HttpServletRequest request, long userId) {
    HttpSession previous = request.getSession(false);
    if (previous != null) {
      previous.invalidate();
    }
    // A fresh session prevents reuse of an identifier established before this login.
    request.getSession(true).setAttribute(USER_ID, userId);
  }

  public long requireUserId(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new SessionRequiredException();
    }
    try {
      if (session.getAttribute(USER_ID) instanceof Long userId) {
        return userId;
      }
    } catch (IllegalStateException exception) {
      // The session may have been invalidated by another request.
      throw new SessionRequiredException();
    }
    throw new SessionRequiredException();
  }
}
