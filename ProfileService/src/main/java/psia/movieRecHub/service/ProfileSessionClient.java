package psia.movieRecHub.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import psia.movieRecHub.dto.UserSessionResponse;

@Component
public class ProfileSessionClient {

  private final RestClient client;

  public ProfileSessionClient(
      RestClient.Builder builder,
      @Value("${user.service.base-url}") String baseUrl,
      @Value("${user.service.connect-timeout}") Duration connectTimeout,
      @Value("${user.service.read-timeout}") Duration readTimeout) {
    if (connectTimeout.isZero() || connectTimeout.isNegative()
        || readTimeout.isZero() || readTimeout.isNegative()) {
      throw new IllegalArgumentException("UserService HTTP timeouts must be positive");
    }
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    client = builder.baseUrl(baseUrl).requestFactory(factory).build();
  }

  public long requireUserId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      throw new ProfileAuthenticationRequiredException();
    }
    try {
      UserSessionResponse response = client.get().uri("/internal/auth/session")
          .header(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId)
          .retrieve().body(UserSessionResponse.class);
      if (response == null || response.userId() <= 0) {
        throw new SessionVerificationUnavailableException();
      }
      return response.userId();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
        throw new ProfileAuthenticationRequiredException();
      }
      throw new SessionVerificationUnavailableException();
    } catch (RestClientException exception) {
      throw new SessionVerificationUnavailableException();
    }
  }
}
