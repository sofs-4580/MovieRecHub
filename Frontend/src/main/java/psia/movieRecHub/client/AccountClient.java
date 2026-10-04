package psia.movieRecHub.client;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import psia.movieRecHub.dto.LoginResponse;

/** Account operations of UserService: register, login, logout, change password. */
@Component
public class AccountClient {

  /** Result of a successful login: who, and the Set-Cookie header(s) to hand to the browser. */
  public record LoginResult(Long userId, String username, List<String> sessionCookies) {}

  private static final String SERVICE = "The account service";

  private final ServiceCaller caller;
  private final String baseUrl;

  public AccountClient(ServiceCaller caller, @Value("${user.service.url}") String baseUrl) {
    this.caller = caller;
    this.baseUrl = baseUrl;
  }

  public void register(String username, String password, List<String> favoriteGenres) {
    caller.exchange(
        SERVICE,
        HttpMethod.POST,
        baseUrl + "/api/auth/register",
        json(null),
        Map.of("username", username, "password", password, "favoriteGenres", favoriteGenres),
        Void.class);
  }

  public LoginResult login(String username, String password) {
    ResponseEntity<LoginResponse> response =
        caller.exchange(
            SERVICE,
            HttpMethod.POST,
            baseUrl + "/api/auth/login",
            json(null),
            Map.of("username", username, "password", password),
            LoginResponse.class);
    // Only the UserService session cookie is passed on to the browser, nothing else.
    List<String> cookies =
        response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
            .filter(cookie -> cookie.startsWith("JSESSIONID="))
            .toList();
    LoginResponse body = response.getBody();
    if (body == null || cookies.isEmpty()) {
      throw new ApiException(502, "BAD_RESPONSE", SERVICE + " did not start a session");
    }
    return new LoginResult(body.userId(), body.username(), cookies);
  }

  /** Best effort: the browser cookie is dropped by the caller either way. */
  public void logout(String sessionId) {
    try {
      caller.exchange(
          SERVICE,
          HttpMethod.POST,
          baseUrl + "/api/auth/logout",
          json(sessionId),
          null,
          Void.class);
    } catch (ApiException ignored) {
      // an expired session or an unavailable service must not block signing out locally
    }
  }

  public void changePassword(String sessionId, String currentPassword, String newPassword) {
    caller.exchange(
        SERVICE,
        HttpMethod.PATCH,
        baseUrl + "/api/users/me/password",
        json(sessionId),
        Map.of("currentPassword", currentPassword, "newPassword", newPassword),
        Void.class);
  }

  private HttpHeaders json(String sessionId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    if (sessionId != null) {
      headers.set(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId);
    }
    return headers;
  }
}
