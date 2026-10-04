package psia.movieRecHub.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class UserSessionClient {

  private final RestTemplate restTemplate;
  private final String userServiceUrl;

  public UserSessionClient(
      RestTemplate restTemplate, @Value("${user.service.url}") String userServiceUrl) {
    this.restTemplate = restTemplate;
    this.userServiceUrl = userServiceUrl;
  }

  /** Returns the logged-in user for this UserService session id, or null if not logged in. */
  public AuthenticatedSessionResponse findSession(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId);
    try {
      AuthenticatedSessionResponse response =
          restTemplate
              .exchange(
                  userServiceUrl + "/internal/auth/session",
                  HttpMethod.GET,
                  new HttpEntity<Void>(headers),
                  AuthenticatedSessionResponse.class)
              .getBody();
      return response == null || response.userId() <= 0 ? null : response;
    } catch (HttpClientErrorException exception) {
      if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
        return null;
      }
      throw new UserServiceException("Could not validate the current user session", exception);
    } catch (RestClientException exception) {
      throw new UserServiceException("UserService is unavailable", exception);
    }
  }
}
