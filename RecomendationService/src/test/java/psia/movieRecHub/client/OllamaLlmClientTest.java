package psia.movieRecHub.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import psia.movieRecHub.exceptions.LlmUnavailableException;

@ExtendWith(MockitoExtension.class)
class OllamaLlmClientTest {

  private static final String URL = "http://ollama:11434/api/chat";
  private static final Map<String, Object> SCHEMA = Map.of("type", "object");

  @Mock RestTemplate rest;

  OllamaLlmClient client;

  @BeforeEach
  void setUp() {
    client = new OllamaLlmClient(rest, "http://ollama:11434", "qwen", 200, 4096, "30m");
  }

  private void answer(OllamaLlmClient.ChatResponse response) {
    when(rest.postForObject(eq(URL), any(), eq(OllamaLlmClient.ChatResponse.class)))
        .thenReturn(response);
  }

  @Test
  void returnsTheTextTheModelWrote() {
    answer(new OllamaLlmClient.ChatResponse(new OllamaLlmClient.Message("{\"ids\":[1]}")));

    assertEquals("{\"ids\":[1]}", client.generateJson("system", "user", SCHEMA));
  }

  @Test
  @SuppressWarnings("unchecked")
  void asksForStructuredOutputWithTemperatureZero() {
    answer(new OllamaLlmClient.ChatResponse(new OllamaLlmClient.Message("{}")));

    client.generateJson("the system", "the user", SCHEMA);

    ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
    verify(rest).postForObject(eq(URL), body.capture(), eq(OllamaLlmClient.ChatResponse.class));
    Map<String, Object> sent = (Map<String, Object>) body.getValue();
    assertEquals("qwen", sent.get("model"));
    assertEquals(false, sent.get("stream"));
    assertEquals(SCHEMA, sent.get("format"));
    assertEquals("30m", sent.get("keep_alive"));
    Map<String, Object> options = (Map<String, Object>) sent.get("options");
    assertEquals(0, options.get("temperature"));
    assertEquals(200, options.get("num_predict"));
    assertEquals(4096, options.get("num_ctx"));
    List<Map<String, String>> messages = (List<Map<String, String>>) sent.get("messages");
    assertEquals("system", messages.get(0).get("role"));
    assertEquals("the system", messages.get(0).get("content"));
    assertEquals("user", messages.get(1).get("role"));
    assertEquals("the user", messages.get(1).get("content"));
  }

  @Test
  void anEmptyAnswerMeansTheModelIsUnavailable() {
    answer(null);
    assertThrows(LlmUnavailableException.class, () -> client.generateJson("s", "u", SCHEMA));
  }

  @Test
  void aBlankAnswerMeansTheModelIsUnavailable() {
    answer(new OllamaLlmClient.ChatResponse(new OllamaLlmClient.Message("  ")));
    assertThrows(LlmUnavailableException.class, () -> client.generateJson("s", "u", SCHEMA));
  }

  @Test
  void networkErrorsBecomeLlmUnavailable() {
    when(rest.postForObject(eq(URL), any(), eq(OllamaLlmClient.ChatResponse.class)))
        .thenThrow(new ResourceAccessException("timeout"));

    assertThrows(LlmUnavailableException.class, () -> client.generateJson("s", "u", SCHEMA));
  }
}
