package psia.movieRecHub.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import psia.movieRecHub.client.UserServiceException;
import psia.movieRecHub.client.UserSessionClient;

@Component
@RequestScope
public class CurrentUser {

  private final HttpServletRequest request;
  private final UserSessionClient userSessionClient;
  private boolean resolved;
  private boolean userServiceUnavailable;
  private Long userId;

  public CurrentUser(HttpServletRequest request, UserSessionClient userSessionClient) {
    this.request = request;
    this.userSessionClient = userSessionClient;
  }

  public Long getId() {
    if (resolved) {
      return userId;
    }
    resolved = true;

    String sessionId = sessionId();
    if (sessionId == null) {
      return null;
    }
    try {
      userId = userSessionClient.findUserId(sessionId);
    } catch (UserServiceException exception) {
      userServiceUnavailable = true;
    }
    return userId;
  }

  public boolean isUserServiceUnavailable() {
    getId();
    return userServiceUnavailable;
  }

  private String sessionId() {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if ("JSESSIONID".equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
