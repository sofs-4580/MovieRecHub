package psia.movieRecHub.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Calls UserService / ProfileService and turns every failure into an ApiException. Those services
 * answer errors as {"code": "...", "message": "..."}.
 */
@Component
public class ServiceCaller {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  public ServiceCaller(RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
  }

  public <T> ResponseEntity<T> exchange(
      String serviceName,
      HttpMethod method,
      String url,
      HttpHeaders headers,
      Object body,
      Class<T> type,
      Object... uriVars) {
    try {
      return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), type, uriVars);
    } catch (RestClientResponseException ex) {
      throw fromResponse(ex);
    } catch (ResourceAccessException ex) {
      throw new ApiException(
          503, "SERVICE_UNAVAILABLE", serviceName + " is not available right now");
    } catch (RestClientException ex) {
      throw new ApiException(502, "BAD_RESPONSE", serviceName + " sent an unexpected answer");
    }
  }

  private ApiException fromResponse(RestClientResponseException ex) {
    int status = ex.getStatusCode().value();
    String code = "HTTP_" + status;
    String message = "Request failed (" + status + ")";
    try {
      JsonNode json = objectMapper.readTree(ex.getResponseBodyAsString());
      if (json.hasNonNull("code")) {
        code = json.get("code").asText();
      }
      if (json.hasNonNull("message")) {
        message = json.get("message").asText();
      }
    } catch (Exception ignored) {
      // empty or non-JSON body: keep the generic message
    }
    return new ApiException(status, code, message);
  }
}
