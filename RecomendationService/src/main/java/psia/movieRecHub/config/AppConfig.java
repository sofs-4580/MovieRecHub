package psia.movieRecHub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

  @Bean
  RestTemplate movieRestTemplate(
      @Value("${movie.service.connect-timeout-ms}") int connectMs,
      @Value("${movie.service.read-timeout-ms}") int readMs) {
    return build(connectMs, readMs);
  }

  private static RestTemplate build(int connectMs, int readMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectMs);
    factory.setReadTimeout(readMs);
    return new RestTemplate(factory);
  }
}
