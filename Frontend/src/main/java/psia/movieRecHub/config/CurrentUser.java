package psia.movieRecHub.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import psia.movieRecHub.client.AuthenticatedSessionResponse;
import psia.movieRecHub.client.UserServiceException;
import psia.movieRecHub.client.UserSessionClient;

/**
 * The logged-in user of the current request. The login session lives in UserService; the browser
 * holds its JSESSIONID cookie (relayed at login, see AuthWebController) and we validate it with
 * UserService once per request.
 */
@Component
@RequestScope
public class CurrentUser {

  public static final String SESSION_COOKIE = "JSESSIONID";

  private final HttpServletRequest request;
  private final UserSessionClient userSessionClient;
  private boolean resolved;
  private boolean userServiceUnavailable;
  private AuthenticatedSessionResponse session;

  public CurrentUser(HttpServletRequest request, UserSessionClient userSessionClient) {
    this.request = request;
    this.userSessionClient = userSessionClient;
  }

  private void resolve() {
    if (resolved) {
      return;
    }
    resolved = true;
    String sessionId = getSessionId();
    if (sessionId == null) {
      return;
    }
    try {
      session = userSessionClient.findSession(sessionId);
    } catch (UserServiceException exception) {
      userServiceUnavailable = true;
    }
  }

  public Long getId() {
    resolve();
    return session == null ? null : session.userId();
  }

  /** Falls back to "user N" when UserService does not return the username. */
  public String getUsername() {
    resolve();
    if (session == null) {
      return null;
    }
    return session.username() != null ? session.username() : "user " + session.userId();
  }

  public boolean isUserServiceUnavailable() {
    resolve();
    return userServiceUnavailable;
  }

  /** The raw UserService session id from the browser cookie (not validated). */
  public String getSessionId() {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (SESSION_COOKIE.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
