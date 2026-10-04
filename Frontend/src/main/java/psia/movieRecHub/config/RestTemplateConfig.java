package psia.movieRecHub.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

  /**
   * Uses the JDK HttpClient instead of HttpURLConnection because HttpURLConnection cannot send
   * PATCH (password / profile updates) and throws on a 401 answer to a POST (wrong password at
   * login).
   */
  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(10));
    // Going through the builder keeps Spring Boot's JSON configuration (dates, unknown fields).
    return builder.requestFactory(() -> factory).build();
  }
}
