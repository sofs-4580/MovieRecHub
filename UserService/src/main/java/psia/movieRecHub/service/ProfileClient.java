package psia.movieRecHub.service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProfileClient {

  private final RestClient client;

  public ProfileClient(
      RestClient.Builder builder,
      @Value("${profile.service.base-url}") String baseUrl,
      @Value("${profile.service.connect-timeout}") Duration connectTimeout,
      @Value("${profile.service.read-timeout}") Duration readTimeout) {
    if (connectTimeout.isZero() || connectTimeout.isNegative()
        || readTimeout.isZero() || readTimeout.isNegative()) {
      throw new IllegalArgumentException("Profile HTTP timeouts must be positive");
    }
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    client = builder.baseUrl(baseUrl).requestFactory(factory).build();
  }

  public void createInitialProfile(long userId, String username, Set<String> favoriteGenres) {
    var request = client.put().uri("/internal/profiles/{userId}", userId);
    int status = request.body(Map.of("username", username, "favoriteGenres", favoriteGenres))
        .retrieve().toBodilessEntity().getStatusCode().value();
    if (status != 201 && status != 204) {
      throw new RestClientException("Unexpected profile creation status: " + status);
    }
  }
}
