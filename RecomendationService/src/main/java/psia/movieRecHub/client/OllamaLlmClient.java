package psia.movieRecHub.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import psia.movieRecHub.exceptions.LlmUnavailableException;

@Component
public class OllamaLlmClient {

  // Só nos interessa o texto da resposta
  @JsonIgnoreProperties(ignoreUnknown = true)
  record ChatResponse(Message message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Message(String content) {}

  private final RestTemplate rest;
  private final String baseUrl;
  private final String model;
  private final int numPredict;
  private final int numCtx;
  private final String keepAlive;

  public OllamaLlmClient(
      @Qualifier("llmRestTemplate") RestTemplate rest,
      @Value("${llm.url}") String baseUrl,
      @Value("${llm.model}") String model,
      @Value("${llm.num-predict}") int numPredict,
      @Value("${llm.num-ctx}") int numCtx,
      @Value("${llm.keep-alive}") String keepAlive) {
    this.rest = rest;
    this.baseUrl = baseUrl;
    this.model = model;
    this.numPredict = numPredict;
    this.numCtx = numCtx;
    this.keepAlive = keepAlive;
  }

  public String modelName() {
    return model;
  }

  /**
   * Pergunta ao modelo e obriga-o a responder em JSON com a forma descrita em "schema". Devolve o
   * texto JSON tal como o modelo o escreveu (ainda por validar!).
   */
  public String generateJson(String system, String user, Map<String, Object> schema) {
    Map<String, Object> body =
        Map.of(
            "model",
            model,
            "stream",
            false,
            "format",
            schema,
            "keep_alive",
            keepAlive,
            "options",
            Map.of("temperature", 0, "num_predict", numPredict, "num_ctx", numCtx),
            "messages",
            List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
    try {
      ChatResponse response = rest.postForObject(baseUrl + "/api/chat", body, ChatResponse.class);
      if (response == null
          || response.message() == null
          || response.message().content() == null
          || response.message().content().isBlank()) {
        throw new LlmUnavailableException("Empty answer from the model");
      }
      return response.message().content();
    } catch (RestClientException e) {
      throw new LlmUnavailableException("LLM backend error: " + e.getMessage(), e);
    }
  }
}
