package psia.movieRecHub.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import psia.movieRecHub.dto.UsernameLookupResponse;

@Component
public class ProfileUsernameClient {

  private final RestClient client;

  public ProfileUsernameClient(RestClient.Builder builder,
      @Value("${user.service.base-url}") String baseUrl,
      @Value("${user.service.connect-timeout}") Duration connectTimeout,
      @Value("${user.service.read-timeout}") Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    client = builder.baseUrl(baseUrl).requestFactory(factory).build();
  }

  public String usernameFor(long userId) {
    UsernameLookupResponse response = client.get()
        .uri("/internal/users/{userId}/username", userId)
        .retrieve().body(UsernameLookupResponse.class);
    if (response == null || response.userId() != userId || response.username() == null
        || !response.username().matches("[a-z0-9_]{3,30}")) {
      throw new RestClientException("Invalid username lookup response");
    }
    return response.username();
  }
}
