package psia.movieRecHub.client;

import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import psia.movieRecHub.dto.RecommendationAskDto;
import psia.movieRecHub.dto.RecommendationProfileDto;
import psia.movieRecHub.dto.RecommendationResultDto;

@Component
public class RecommendationServiceClient {

  private static final String USER_HEADER = "X-User-Id";

  // para o bloco do login e para o perfil se demorar e melhor desistir
  private final RestTemplate quick;

  // para o pedido e prompt, llm pode demorar
  private final RestTemplate slow;

  private final String baseUrl;

  public RecommendationServiceClient(
      RestTemplateBuilder builder, @Value("${recommendation.service.url}") String baseUrl) {
    this.quick =
        builder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(3)).build();
    this.slow =
        builder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(120)).build();
    this.baseUrl = baseUrl;
  }

  // recomendacoes em segundo plano, le da cache. pending se ainda n ha
  public RecommendationResultDto forUser(Long userId) {
    return call(
        quick, HttpMethod.GET, "/api/recommendations", userId, null, RecommendationResultDto.class);
  }

  // pedido em prompt
  public RecommendationResultDto ask(Long userId, String text) {
    return call(
        slow,
        HttpMethod.POST,
        "/api/recommendations",
        userId,
        new RecommendationAskDto(text),
        RecommendationResultDto.class);
  }

  // avisar o servico do perfil (generos e decadas) para o agente o usar
  public void saveProfile(Long userId, Set<String> genres, Set<Integer> decades) {
    call(
        quick,
        HttpMethod.PUT,
        "/api/recommendations/users/{id}/profile",
        userId,
        new RecommendationProfileDto(genres, decades),
        Void.class,
        userId);
  }

  private <T> T call(
      RestTemplate rest,
      HttpMethod method,
      String path,
      Long userId,
      Object body,
      Class<T> type,
      Object... uriVars) {
    HttpHeaders headers = new HttpHeaders();
    if (userId != null) {
      headers.set(USER_HEADER, String.valueOf(userId));
    }
    try {
      return rest.exchange(baseUrl + path, method, new HttpEntity<>(body, headers), type, uriVars)
          .getBody();
    } catch (RestClientResponseException ex) {
      throw new RecommendationServiceException(
          "Recommendation service failed (" + ex.getStatusCode().value() + ")");
    } catch (ResourceAccessException ex) {
      throw new RecommendationServiceException("Recommendation service is not available");
    } catch (RestClientException ex) {
      throw new RecommendationServiceException("Recommendation service sent an unexpected answer");
    }
  }
}
